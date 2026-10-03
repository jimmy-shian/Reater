package com.reater.app.ui.share

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.remote.FetchedPostResult
import com.reater.app.data.remote.ThreadsGraphQLClient
import com.reater.app.data.repository.SettingsRepository
import com.reater.app.data.repository.ThreadPostRepository
import com.reater.app.domain.UrlParser
import com.reater.app.notify.NotifyCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareSaveUiState(
    val rawText: String = "",
    val targetUrl: String = "",
    /** 實際拿去抓取的 URL（/share/ 保留原樣，不可被 canonical 蓋掉） */
    val fetchUrl: String = "",
    /** 串文 / 短鏈 / 留言/分享（供 UI 顯示） */
    val shareKindLabel: String = "",
    val shortcode: String = "",
    val authorHandle: String = "",
    val bodyText: String = "",
    val commentsText: String = "",
    val manualNote: String = "",
    val selectedCategoryId: Long? = null,
    /** 自動預選依據：""＝無，"topic"＝同主題紀錄，"author"＝同作者紀錄（供 UI 提示用） */
    val suggestedBasis: String = "",
    val isFavorite: Boolean = false,
    /** 分享文字扣除 URL 後的草稿（抓取失敗時的內文兜底；抓到正文時會被取代） */
    val bodyDraft: String = "",
    /** 原始連結是否帶 /media 尾綴（代表該貼文含圖片/影片，供 UI 顯示提示） */
    val hadMediaSuffix: Boolean = false,
    /** 內建瀏覽器（WebView）正在跑 JS 解析 /share/ 跳轉 */
    val webResolving: Boolean = false,
    /** WebView 解析是否已嘗試過（避免重組重複觸發） */
    val webResolveAttempted: Boolean = false,
    val isFetching: Boolean = false,
    val isFetchFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val showCreateCategoryDialog: Boolean = false,
    val showProLimitNotice: Boolean = false,
    val fetchedResult: FetchedPostResult? = null
)

@HiltViewModel
class ShareSaveViewModel @Inject constructor(
    private val repository: ThreadPostRepository,
    private val graphQLClient: ThreadsGraphQLClient,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareSaveUiState())
    val uiState: StateFlow<ShareSaveUiState> = _uiState.asStateFlow()

    // 兩條抓取路徑各自保存結果，最後 merge（HTTP 失敗但 WebView 成功也算成功）
    private var httpResult: FetchedPostResult? = null
    private var webResult: FetchedPostResult? = null
    private var httpDone = false
    private var webDone = false
    private var lastAppliedComments = ""
    /** 使用者是否已手動選過分類（手動選擇後，歷史預選不再覆蓋） */
    private var userPickedCategory = false
    private var suggestionJob: Job? = null

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isProUnlocked: StateFlow<Boolean> = settingsRepository.isProUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 外觀跟隨主 App（主題 / 字級）
    val themeMode: StateFlow<String> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val fontScale: StateFlow<Float> = settingsRepository.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    init {
        viewModelScope.launch {
            val lastId = settingsRepository.lastSelectedCategoryId.first()
            if (lastId != null && _uiState.value.selectedCategoryId == null) {
                _uiState.update { it.copy(selectedCategoryId = lastId) }
            }
        }
    }

    fun processIncomingText(sharedText: String) {
        _uiState.update { it.copy(rawText = sharedText) }
        val extractedUrl = UrlParser.extractFirstThreadsUrl(sharedText) ?: sharedText.trim()
        val parsed = UrlParser.parseAndCanonicalize(extractedUrl)

        // 儲存用 canonical（/share/ 直接保留原鏈，瀏覽器可開啟）；
        // 抓取用 original（/share/ 必須跟 redirect，不可用 /t/ 蓋掉）。
        // /share/ 的短碼未知（token ≠ 短碼），先用 share_<token> 佔位。
        val targetUrl = parsed?.canonicalUrl ?: extractedUrl
        val fetchUrl = parsed?.originalUrl ?: extractedUrl
        val shortcode = when {
            parsed == null -> "sc_${System.currentTimeMillis()}"
            parsed.shortcode.isNotBlank() -> parsed.shortcode
            parsed.shareToken.isNotBlank() -> "share_${parsed.shareToken}"
            else -> "sc_${System.currentTimeMillis()}"
        }
        val initialHandle = parsed?.handle ?: ""
        val kindLabel = parsed?.let {
            with(UrlParser) { it.shareKindLabel }
        }.orEmpty()
        // 分享文字常自帶全文（URL + 內文）：先預填，使用者立刻看得到內容；
        // 若抓取拿到更權威的正文再取代（見 fetchRemoteData）。
        val draft = UrlParser.extractBodyDraft(sharedText, extractedUrl)

        httpResult = null
        webResult = null
        httpDone = false
        webDone = false
        lastAppliedComments = ""
        userPickedCategory = false
        suggestionJob?.cancel()
        suggestionJob = null

        _uiState.update {
            it.copy(
                targetUrl = targetUrl,
                fetchUrl = fetchUrl,
                shareKindLabel = kindLabel,
                shortcode = shortcode,
                authorHandle = initialHandle,
                bodyText = draft,
                bodyDraft = draft,
                hadMediaSuffix = parsed?.hadMediaSuffix == true,
                isFetching = true,
                isFetchFailed = false,
                webResolving = false,
                webResolveAttempted = false,
                fetchedResult = null
            )
        }

        // 連結本身已帶作者時先做一次同作者預選；抓取完成後再以主題＋作者重算
        refreshCategorySuggestion()

        fetchRemoteData(fetchUrl, shortcode)
    }

    /**
     * 依過往儲存紀錄預選分類：同主題（topicTag）優先、同作者其次；
     * 各自取最常用分類（次數相同取最近使用，見 ItemDao）。
     * 僅在使用者尚未手動選擇時套用，且套用前再次確認作者/主題未變（避免競態）。
     */
    private fun refreshCategorySuggestion() {
        if (userPickedCategory) return
        val author = _uiState.value.authorHandle.trim()
        val topic = _uiState.value.fetchedResult?.topicTag?.trim().orEmpty()
        if (author.isBlank() && topic.isBlank()) return
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            val suggested = runCatching {
                repository.suggestCategoryId(author, topic)
            }.getOrNull() ?: return@launch
            if (userPickedCategory) return@launch
            val current = _uiState.value
            if (current.authorHandle.trim() != author) return@launch
            if (current.fetchedResult?.topicTag?.trim().orEmpty() != topic) return@launch
            _uiState.update {
                it.copy(
                    selectedCategoryId = suggested,
                    suggestedBasis = if (topic.isNotBlank()) "topic" else "author"
                )
            }
        }
    }

    private fun fetchRemoteData(urlOrTarget: String, shortcode: String) {
        viewModelScope.launch {
            val result = graphQLClient.fetchPostByPostIdOrShortcode(urlOrTarget, shortcode)
            result.onSuccess { fetched ->
                httpResult = fetched
            }
            httpDone = true
            applyMerged()
        }
    }

    /**
     * 合併 HTTP 與 WebView 兩路結果並更新 UI。
     * 規則：正文取較長者、媒體依 remoteUrl 去重合併、留言依 author+text 去重
     * （後到者帶 media 則補上）、任一路 COMPLETE 即 COMPLETE、兩路皆敗才算失敗。
     */
    private fun applyMerged() {
        val merged = mergeFetched(httpResult, webResult)
        _uiState.update { current ->
            val webPending = current.webResolveAttempted && !webDone
            val anyPending = !httpDone || webPending
            if (merged == null) {
                current.copy(
                    isFetching = anyPending,
                    webResolving = webPending,
                    isFetchFailed = !anyPending
                )
            } else {
                val useHandsOffBody = current.bodyText.isBlank() ||
                    current.bodyText == current.bodyDraft
                // 子串分享（母文存在時）：內文預覽同時保留母文 + 分享留言，否則母文會遺失
                // 例 D-J：【母文 @yw202087】下一位勇者… + 分享 @l.m.sheng_1024 永遠空租吧
                // 「留言的留言」為多層鏈：【母文 @A】a + 分享 @B b + 分享 @主 c（詳情頁逐塊渲染）
                val displayBody = if (merged.parentChain.isNotEmpty() &&
                    merged.parentChain.first().shortcode.isNotBlank() &&
                    merged.parentChain.first().bodyText.isNotBlank()
                ) {
                    buildThreadDisplayBody(
                        merged.parentChain,
                        merged.authorHandle,
                        merged.bodyText
                    )
                } else if (merged.parentShortcode.isNotBlank() && merged.parentBodyText.isNotBlank()) {
                    "【母文 @${merged.parentAuthorHandle}】${merged.parentBodyText}\n\n--- 分享 @${merged.authorHandle} ---\n${merged.bodyText}"
                } else {
                    merged.bodyText
                }
                val resolvedBody = when {
                    displayBody.isNotBlank() && useHandsOffBody -> displayBody
                    else -> current.bodyText
                }
                val commentsJoined = merged.comments.joinToString("\n---\n") { "${it.author}: ${it.text}" }
                val resolvedComments = when {
                    commentsJoined.isBlank() -> current.commentsText
                    current.commentsText.isBlank() || current.commentsText == lastAppliedComments -> {
                        lastAppliedComments = commentsJoined
                        commentsJoined
                    }
                    else -> current.commentsText
                }
                current.copy(
                    isFetching = anyPending,
                    webResolving = webPending,
                    isFetchFailed = merged.bodyText.isBlank() &&
                        merged.media.isEmpty() &&
                        merged.parentMedia.isEmpty() &&
                        merged.comments.isEmpty() &&
                        merged.parentBodyText.isBlank(),
                    // resolvedUrl 是 redirect 最終 URL，常帶 ?xmt= / ?slof= 追蹤參數：
                    // 存檔前先正規化為 canonical，否則會蓋掉乾淨版並污染後續分享文字
                    targetUrl = merged.resolvedUrl.ifBlank { "" }.let { raw ->
                        if (raw.isBlank()) current.targetUrl
                        else UrlParser.parseAndCanonicalize(raw)?.canonicalUrl
                            ?: UrlParser.stripTrackingParams(raw).ifBlank { current.targetUrl }
                    },
                    shortcode = pickShortcode(current.shortcode, merged.shortcode),
                    authorHandle = pickHandle(current.authorHandle, merged.authorHandle),
                    bodyText = resolvedBody,
                    commentsText = resolvedComments,
                    fetchedResult = merged
                )
            }
        }
        // 抓取帶來作者/主題資訊：以過往同主題（優先）/同作者紀錄重算預選分類
        if (merged != null) {
            refreshCategorySuggestion()
        }
    }

    /**
     * 組裝 N 層螺紋鏈存檔內文：
     * 【母文 @root】rootBody\n\n--- 分享 @mid ---\nmidBody…\n\n--- 分享 @main ---\nmainBody。
     * 詳情頁 parseThreadChain 會逐段拆回區塊渲染。
     */
    private fun buildThreadDisplayBody(
        chain: List<com.reater.app.data.remote.threads.ThreadsSjsParser.ParentPost>,
        mainAuthor: String,
        mainBody: String
    ): String {
        val sb = StringBuilder()
        val root = chain.first()
        sb.append("【母文 @${root.authorHandle}】${root.bodyText}")
        for (mid in chain.drop(1)) {
            sb.append("\n\n--- 分享 @${mid.authorHandle} ---\n${mid.bodyText}")
        }
        sb.append("\n\n--- 分享 @${mainAuthor} ---\n${mainBody}")
        return sb.toString()
    }

    private fun pickShortcode(current: String, incoming: String): String {
        if (incoming.isBlank()) return current
        val good = !incoming.startsWith("share_") && !incoming.startsWith("sc_")
        val currentGood = !current.startsWith("share_") && !current.startsWith("sc_")
        return if (good || !currentGood) incoming else current
    }

    private fun pickHandle(current: String, incoming: String): String {        val inOk = incoming.isNotBlank() && incoming != "threads_user"
        val curOk = current.isNotBlank() && current != "threads_user"
        return when {
            inOk && !curOk -> incoming
            curOk && !inOk -> current
            inOk -> incoming // 兩者皆有效：以抓到的為準
            else -> current
        }
    }

    /**
     * 內建瀏覽器解析流程（/share/ 專用，由 ShareSaveActivity 觸發）：
     * WebView 已跑完 JS 跳轉並抽出 SJS，這裡直接用真短碼解析 + 下載媒體。
     */
    fun markWebResolveStarted() {
        _uiState.update { it.copy(webResolving = true, webResolveAttempted = true) }
    }

    fun onWebResolved(
        finalUrl: String,
        sjsBlocks: List<String>,
        renderedText: String = "",
        renderedHtml: String = "",
        domComments: List<com.reater.app.data.remote.threads.ThreadsWebResolver.DomComment> = emptyList()
    ) {
        viewModelScope.launch {
            val result = graphQLClient.resolveFromRenderedBlocks(sjsBlocks, finalUrl, renderedText, renderedHtml, domComments)
            result.onSuccess { fetched ->
                webResult = fetched
            }
            webDone = true
            applyMerged()
        }
    }

    fun onWebResolveFailed() {
        webDone = true
        applyMerged()
    }

    /**
     * 合併 HTTP 與 WebView 兩路的抓取結果：
     * - 正文取較長（分享草稿之外更完整的來源）
     * - 媒體依 remoteUrl 去重聯集
     * - 留言依 author+text 去重（保留帶 media 的那則）
     * - status：任一路 COMPLETE 即 COMPLETE
     * - authorHandle/shortcode：取非 placeholder 者
     */
    private fun mergeFetched(a: FetchedPostResult?, b: FetchedPostResult?): FetchedPostResult? {
        if (a == null) return b
        if (b == null) return a
        val primary = if (a.bodyText.length >= b.bodyText.length) a else b
        val secondary = if (primary === a) b else a
        val body = primary.bodyText.ifBlank { secondary.bodyText }
        // 同一內容不同清晰度 URL 只留一份，避免詳情頁出現雙份影片/圖片
        val media = com.reater.app.data.remote.MediaDedup.distinctFetched(a.media + b.media)
        val commentMap = LinkedHashMap<String, com.reater.app.data.remote.FetchedComment>()
        for (c in (a.comments + b.comments)) {
            val key = c.author + "\u0000" + c.text.trim()
            val prev = commentMap[key]
            if (prev == null || (prev.media.isEmpty() && c.media.isNotEmpty())) {
                commentMap[key] = c
            }
        }
        val comments = commentMap.values.toList()
        val status = if (a.status == "COMPLETE" || b.status == "COMPLETE") "COMPLETE"
        else if (comments.isNotEmpty() && body.isNotBlank()) "COMPLETE"
        else "PARTIAL"
        // 母文合併：任一路有母文即保留（子串分享如 D-JnnrknO/DzVwcgDJw），媒體去重聯集；
        // 祖先鏈取較長者（「留言的留言」多層鏈優先保留中間層）
        val parent = when {
            a.parentShortcode.isNotBlank() && b.parentShortcode.isNotBlank() ->
                if (a.parentBodyText.length >= b.parentBodyText.length) a else b
            a.parentShortcode.isNotBlank() -> a
            else -> b
        }
        fun chainLen(chain: List<com.reater.app.data.remote.threads.ThreadsSjsParser.ParentPost>) =
            chain.sumOf { it.bodyText.length }
        val parentChain = if (chainLen(a.parentChain) >= chainLen(b.parentChain)) a.parentChain else b.parentChain
        val parentMedia = com.reater.app.data.remote.MediaDedup.distinctFetched(a.parentMedia + b.parentMedia)
        return primary.copy(
            bodyText = body,
            media = media,
            comments = comments,
            replyCount = comments.size,
            likeCount = maxOf(a.likeCount, b.likeCount),
            status = status,
            authorHandle = pickHandle(a.authorHandle, b.authorHandle),
            authorDisplayName = when {
                primary.authorHandle == pickHandle(a.authorHandle, b.authorHandle) ->
                    primary.authorDisplayName
                secondary.authorHandle == pickHandle(a.authorHandle, b.authorHandle) ->
                    secondary.authorDisplayName
                else -> primary.authorDisplayName
            },
            authorProfileUrl = primary.authorProfileUrl.ifBlank { secondary.authorProfileUrl },
            authorVerified = primary.authorVerified || secondary.authorVerified,
            shortcode = pickShortcode(secondary.shortcode, primary.shortcode)
                .ifBlank { primary.shortcode },
            resolvedUrl = primary.resolvedUrl.ifBlank { secondary.resolvedUrl },
            postedAt = if (primary.postedAt > 0) primary.postedAt else secondary.postedAt,
            rawJsonMin = primary.rawJsonMin.ifBlank { secondary.rawJsonMin },
            parentShortcode = parent.parentShortcode,
            parentAuthorHandle = parent.parentAuthorHandle,
            parentBodyText = parent.parentBodyText,
            parentLikeCount = maxOf(a.parentLikeCount, b.parentLikeCount),
            parentPostedAt = if (parent.parentPostedAt > 0) parent.parentPostedAt else
                maxOf(a.parentPostedAt, b.parentPostedAt),
            parentMedia = parentMedia,
            parentChain = parentChain,
            topicTag = primary.topicTag.ifBlank { secondary.topicTag }
        )
    }

    fun onBodyTextChanged(text: String) {
        _uiState.update { it.copy(bodyText = text) }
    }
    fun onCommentsTextChanged(text: String) {
        _uiState.update { it.copy(commentsText = text) }
    }

    fun onNoteChanged(text: String) {
        _uiState.update { it.copy(manualNote = text) }
    }

    fun onCategorySelected(categoryId: Long?) {
        userPickedCategory = true
        suggestionJob?.cancel()
        _uiState.update { it.copy(selectedCategoryId = categoryId, suggestedBasis = "") }
        viewModelScope.launch {
            settingsRepository.setLastSelectedCategoryId(categoryId)
        }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun setShowCreateCategoryDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateCategoryDialog = show) }
    }

    fun setShowProLimitNotice(show: Boolean) {
        _uiState.update { it.copy(showProLimitNotice = show) }
    }

    fun createCategory(name: String, avatarIcon: String) {
        if (name.isBlank()) return
        val currentCategories = categories.value
        val isPro = isProUnlocked.value

        // 免費版最多 3 個自訂分類（內建 8 分類不計入）
        val customCount = currentCategories.count { !it.isDefault }
        if (!isPro && customCount >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT) {
            _uiState.update { it.copy(showProLimitNotice = true, showCreateCategoryDialog = false) }
            return
        }

        viewModelScope.launch {
            val newCat = CategoryEntity(
                name = name.trim(),
                colorArgb = 0xFF5C6BC0.toInt(),
                avatarIcon = avatarIcon.ifBlank { "life" }
            )
            val newId = categoryDao.insertCategory(newCat)
            settingsRepository.setLastSelectedCategoryId(newId)
            userPickedCategory = true
            suggestionJob?.cancel()
            _uiState.update {
                it.copy(
                    selectedCategoryId = newId,
                    suggestedBasis = "",
                    showCreateCategoryDialog = false
                )
            }
        }
    }

    fun savePost() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val itemId = repository.savePost(
                    canonicalUrl = state.targetUrl.ifBlank { "https://www.threads.com/unknown/${System.currentTimeMillis()}" },
                    shortcode = state.shortcode.ifBlank { "sc_${System.currentTimeMillis()}" },
                    authorHandle = state.authorHandle.ifBlank { "threads_user" },
                    bodyText = state.bodyText,
                    commentsText = state.commentsText,
                    manualNote = state.manualNote,
                    manualSummary = "",
                    categoryId = state.selectedCategoryId,
                    fetchedResult = state.fetchedResult,
                    isFavorite = state.isFavorite
                )
                // 儲存後未讀提醒（設定可調分鐘數 / 開關）
                runCatching {
                    if (settingsRepository.unreadNudgeEnabled.first()) {
                        NotifyCenter.scheduleUnreadNudge(
                            appContext,
                            itemId,
                            state.bodyText.take(60).ifBlank { "@${state.authorHandle}" },
                            settingsRepository.unreadNudgeDelayMin.first()
                        )
                    }
                }
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
