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

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isProUnlocked: StateFlow<Boolean> = settingsRepository.isProUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 外觀跟隨主 App（主題 / 字級）
    val themeMode: StateFlow<String> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val fontScale: StateFlow<Float> = settingsRepository.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

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
                fetchedResult = null
            )
        }

        fetchRemoteData(fetchUrl, shortcode)
    }

    private fun fetchRemoteData(urlOrTarget: String, shortcode: String) {
        viewModelScope.launch {
            val result = graphQLClient.fetchPostByPostIdOrShortcode(urlOrTarget, shortcode)
            result.onSuccess { fetched ->
                _uiState.update { current ->
                    val commentsJoined = fetched.comments.joinToString("\n---\n") { "${it.author}: ${it.text}" }
                    val fetchFailed = fetched.status == "PARTIAL" &&
                        fetched.bodyText.isBlank() && fetched.media.isEmpty()
                    // 正文採用順序：抓到的正文 > 分享文字草稿 > 使用者已手改的內容。
                    // 若使用者還沒動過內文（空白或仍是草稿），抓到的正文優先取代草稿。
                    val useHandsOffBody = current.bodyText.isBlank() ||
                        current.bodyText == current.bodyDraft
                    val resolvedBody = when {
                        fetched.bodyText.isNotBlank() && useHandsOffBody -> fetched.bodyText
                        else -> current.bodyText
                    }
                    current.copy(
                        isFetching = false,
                        isFetchFailed = fetchFailed,
                        targetUrl = if (fetched.resolvedUrl.isNotBlank()) fetched.resolvedUrl else current.targetUrl,
                        shortcode = if (fetched.shortcode.isNotBlank() && !fetched.shortcode.startsWith("share_")) fetched.shortcode else current.shortcode,
                        authorHandle = if (fetched.authorHandle.isNotBlank() && fetched.authorHandle != "threads_user") fetched.authorHandle else current.authorHandle,
                        bodyText = resolvedBody,
                        commentsText = if (current.commentsText.isBlank() && commentsJoined.isNotBlank()) commentsJoined else current.commentsText,
                        fetchedResult = fetched
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        isFetching = false,
                        isFetchFailed = true
                    )
                }
            }
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
                _uiState.update { current ->
                    val commentsJoined = fetched.comments.joinToString("\n---\n") { "${it.author}: ${it.text}" }
                    val useHandsOffBody = current.bodyText.isBlank() ||
                        current.bodyText == current.bodyDraft
                    current.copy(
                        webResolving = false,
                        isFetching = false,
                        isFetchFailed = false,
                        targetUrl = if (fetched.resolvedUrl.isNotBlank()) fetched.resolvedUrl else current.targetUrl,
                        shortcode = if (fetched.shortcode.isNotBlank()) fetched.shortcode else current.shortcode,
                        authorHandle = if (fetched.authorHandle.isNotBlank() && fetched.authorHandle != "threads_user") fetched.authorHandle else current.authorHandle,
                        bodyText = if (fetched.bodyText.isNotBlank() && useHandsOffBody) fetched.bodyText else current.bodyText,
                        commentsText = if (current.commentsText.isBlank() && commentsJoined.isNotBlank()) commentsJoined else current.commentsText,
                        fetchedResult = fetched
                    )
                }
            }.onFailure {
                onWebResolveFailed()
            }
        }
    }

    fun onWebResolveFailed() {
        _uiState.update { current ->
            current.copy(
                webResolving = false,
                // 草稿/媒體都沒有才算失敗；有草稿就讓使用者直接存
                isFetchFailed = current.bodyText.isBlank() &&
                    current.fetchedResult?.media.isNullOrEmpty()
            )
        }
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
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
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
            _uiState.update {
                it.copy(
                    selectedCategoryId = newId,
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
                    fetchedResult = state.fetchedResult
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
