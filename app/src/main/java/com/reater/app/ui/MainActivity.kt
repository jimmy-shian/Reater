package com.reater.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Share
import com.reater.app.ui.feed.PostCard
import com.reater.app.ui.feed.formatPostShareText
import com.reater.app.ui.feed.ProUnlockCard
import com.reater.app.ui.detail.DetailDialog
import com.reater.app.ui.settings.SettingsDialog
import com.reater.app.ui.components.PasscodeUnlockDialog
import com.reater.app.ui.components.IconGalleryDialog
import com.reater.app.ui.components.UserAvatarView
import com.reater.app.ui.analytics.AnalyticsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.reater.app.ui.components.dismissFocusOnTap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.reater.app.R
import com.reater.app.data.backup.BackupManager
import com.reater.app.data.billing.BillingUiState
import com.reater.app.data.billing.PlayBillingManager
import com.reater.app.data.local.dao.CategoryDao
import com.reater.app.data.local.dao.ProDao
import com.reater.app.data.local.entity.CategoryEntity
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.SavedCollectionEntity
import com.reater.app.data.remote.OpenAiClient
import com.reater.app.data.repository.SettingsRepository
import com.reater.app.data.repository.ThreadPostRepository
import com.reater.app.domain.SmartCollectionEngine
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pro 一鍵開通 Deep Link：冷啟動時先收下，Compose 端 LaunchedEffect 會自動驗證解鎖
        parseProUnlock(intent?.data)?.let { proDeepLinkFlow.value = it }
        setContent {
            MainThemedScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 熱啟動（App 已在前景）：同樣丟進 Flow，Compose 端會接著處理
        parseProUnlock(intent.data)?.let { proDeepLinkFlow.value = it }
    }

    data class ProUnlockDeepLink(val email: String, val code: String)

    companion object {
        /** 待處理的一鍵開通連結（冷/熱啟動共用，Compose 消費後清 null）。 */
        val proDeepLinkFlow = MutableStateFlow<ProUnlockDeepLink?>(null)

        /**
         * 解析 reater://pro-unlock?email=..&code=..（code 也接受 key 別名）
         * 與 https://reater.app/pro-unlock?email=..&code=.. 備用格式。
         */
        fun parseProUnlock(uri: Uri?): ProUnlockDeepLink? {
            if (uri == null) return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val isCustom = scheme == "reater" && uri.host?.lowercase() == "pro-unlock"
            val isHttps = scheme == "https" && uri.host?.lowercase() == "reater.app" &&
                (uri.path?.startsWith("/pro-unlock") == true)
            if (!isCustom && !isHttps) return null
            val email = uri.getQueryParameter("email")?.trim().orEmpty()
            val code = (uri.getQueryParameter("code") ?: uri.getQueryParameter("key"))?.trim().orEmpty()
            if (email.isBlank() || code.isBlank()) return null
            return ProUnlockDeepLink(email = email, code = code)
        }
    }
}

@Composable
private fun MainThemedScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    com.reater.app.ui.theme.ReaterTheme(themeMode = themeMode, fontScale = fontScale) {
        // 系統列跟隨深淺：深色底黑、淺色底白，並切換圖示明暗，避免
        //「深色底部導覽列不黑、淺色頂部文字看不見」
        val context = LocalContext.current
        val darkIcons = themeMode == "light" ||
            (themeMode == "system" && !androidx.compose.foundation.isSystemInDarkTheme())
        androidx.compose.runtime.SideEffect {
            runCatching {
                val activity = context as? android.app.Activity ?: return@runCatching
                val window = activity.window
                val isDark = themeMode == "dark" ||
                    (themeMode == "system" && !darkIcons)
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    ?.let {
                        it.isAppearanceLightStatusBars = darkIcons
                        it.isAppearanceLightNavigationBars = darkIcons
                    }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = if (isDark) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                }
            }
        }
        MainScreen(viewModel = viewModel)
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ThreadPostRepository,
    private val settingsRepository: SettingsRepository,
    private val smartCollectionEngine: SmartCollectionEngine,
    private val proDao: ProDao,
    private val categoryDao: CategoryDao,
    private val openAiClient: OpenAiClient,
    private val billingManager: PlayBillingManager,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _tabIndex = MutableStateFlow(0) // 0: All, 1: Unread, 2: Favorite, 3: Pro 分類, 4: Trash, 5: 分析
    val tabIndex: StateFlow<Int> = _tabIndex

    // PRO Tab 分類篩選：null=全部，-1=未分類，其餘=分類 id
    private val _proCategoryFilter = MutableStateFlow<Long?>(null)
    val proCategoryFilter: StateFlow<Long?> = _proCategoryFilter

    fun setProCategoryFilter(id: Long?) {
        _proCategoryFilter.value = id
    }

    val isProUnlocked: StateFlow<Boolean> = settingsRepository.isProUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val customAvatarId: StateFlow<String> = settingsRepository.customAvatarId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "life")

    val customAvatarUri: StateFlow<String?> = settingsRepository.customAvatarUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setCustomAvatarId(id: String) {
        viewModelScope.launch { settingsRepository.setCustomAvatarId(id) }
    }

    fun setCustomAvatarUri(uri: String?) {
        viewModelScope.launch { settingsRepository.setCustomAvatarUri(uri) }
    }

    val billingUiState: StateFlow<BillingUiState> = billingManager.uiState

    val customBaseUrl: StateFlow<String> = settingsRepository.customBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1")

    val selectedModel: StateFlow<String> = settingsRepository.selectedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gpt-5-nano")

    val aiTransmissionConsent: StateFlow<Boolean> = settingsRepository.aiTransmissionConsent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val smartCollections: StateFlow<List<SavedCollectionEntity>> = proDao.observeAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashPosts: StateFlow<List<ItemDetail>> = repository.observeTrashPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val posts: StateFlow<List<ItemDetail>> = combine(_searchQuery, _tabIndex) { query, tab ->
        Pair(query, tab)
    }.flatMapLatest { (query, tab) ->
        if (query.isNotBlank()) {
            repository.searchPosts(query)
        } else {
            when (tab) {
                1 -> repository.observeUnreadPosts()
                2 -> repository.observeFavoritePosts()
                4 -> repository.observeTrashPosts()
                else -> repository.observeAllPosts()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPosts: StateFlow<List<ItemDetail>> = repository.observeAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadPosts: StateFlow<List<ItemDetail>> = repository.observeUnreadPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePosts: StateFlow<List<ItemDetail>> = repository.observeFavoritePosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            smartCollectionEngine.seedDefaultProCollectionsIfEmpty()
            repository.purgeExpiredTrash()
        }
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun onTabChanged(index: Int) {
        _tabIndex.value = index
    }

    fun toggleRead(item: ItemDetail) {
        viewModelScope.launch {
            repository.toggleReadStatus(item.item.id, !item.isRead)
        }
    }

    fun markAsRead(item: ItemDetail) {
        if (!item.isRead) {
            viewModelScope.launch {
                repository.toggleReadStatus(item.item.id, true)
            }
        }
    }

    fun toggleFavorite(item: ItemDetail) {
        viewModelScope.launch {
            repository.toggleFavoriteStatus(item.item.id, !item.isFavorite)
        }
    }

    /** 詳情頁 ... 選單改分類用 */
    fun updateCategory(itemId: Long, categoryId: Long?) {
        viewModelScope.launch {
            repository.updateCategory(itemId, categoryId)
        }
    }

    // PRO Tab 建分類 Dialog 狀態（免費 3 自訂上限，滿額轉解鎖）
    private val _showCreateCategoryDialog = MutableStateFlow(false)
    val showCreateCategoryDialog: StateFlow<Boolean> = _showCreateCategoryDialog

    private val _createCategoryPrefill = MutableStateFlow("")
    val createCategoryPrefill: StateFlow<String> = _createCategoryPrefill

    private val _showProLimitNotice = MutableStateFlow(false)
    val showProLimitNotice: StateFlow<Boolean> = _showProLimitNotice

    fun setShowCreateCategoryDialog(show: Boolean, prefill: String = "") {
        if (show) _createCategoryPrefill.value = prefill
        _showCreateCategoryDialog.value = show
    }

    fun setShowProLimitNotice(show: Boolean) {
        _showProLimitNotice.value = show
    }

    fun createCategory(name: String, avatarIcon: String) {
        if (name.isBlank()) return
        val customCount = categories.value.count { !it.isDefault }
        if (!isProUnlocked.value && customCount >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT) {
            _showCreateCategoryDialog.value = false
            _showProLimitNotice.value = true
            return
        }
        viewModelScope.launch {
            categoryDao.insertCategory(
                CategoryEntity(
                    name = name.trim(),
                    colorArgb = 0xFF5C6BC0.toInt(),
                    avatarIcon = avatarIcon.ifBlank { "life" }
                )
            )
            _showCreateCategoryDialog.value = false
        }
    }

    // ---------- 分析頁 ----------

    data class AnalyticsUiState(
        val loading: Boolean = true,
        val savedToday: Int = 0,
        val openedToday: Int = 0,
        val totalReviews: Int = 0,
        val dailySaved: List<Pair<String, Int>> = emptyList(),
        val hourlySaved: List<Pair<Int, Int>> = emptyList()
    )

    private val _analytics = MutableStateFlow(AnalyticsUiState())
    val analytics: StateFlow<AnalyticsUiState> = _analytics

    /** 詳情開啟計數（分析頁打開 / 回顧用） */
    fun recordOpen(item: ItemDetail) {
        viewModelScope.launch {
            repository.recordOpen(item.item.id)
            if (_tabIndex.value == 5) loadAnalytics()
        }
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            _analytics.value = AnalyticsUiState(loading = true)
            val pro = isProUnlocked.value
            val today = com.reater.app.ui.components.startOfToday()
            val since = if (pro) {
                today - 29L * 24 * 60 * 60 * 1000
            } else {
                com.reater.app.ui.components.startOfWeek()
            }
            val snap = repository.getAnalytics(since, today)
            _analytics.value = AnalyticsUiState(
                loading = false,
                savedToday = snap.savedToday,
                openedToday = snap.openedToday,
                totalReviews = snap.totalReviews,
                dailySaved = snap.dailySaved,
                hourlySaved = snap.hourlySaved
            )
        }
    }

    // ---------- 外觀（主題 / 字體） ----------

    val themeMode: StateFlow<String> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val fontScale: StateFlow<Float> = settingsRepository.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setFontScale(scale) }
    }

    // ---------- 通知（未讀提醒 / 每日回顧） ----------

    val unreadNudgeEnabled: StateFlow<Boolean> = settingsRepository.unreadNudgeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val unreadNudgeDelayMin: StateFlow<Int> = settingsRepository.unreadNudgeDelayMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val reviewDigestEnabled: StateFlow<Boolean> = settingsRepository.reviewDigestEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reviewDigestHour: StateFlow<Int> = settingsRepository.reviewDigestHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 21)

    fun setUnreadNudgeEnabled(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUnreadNudgeEnabled(enabled)
        }
    }

    fun setUnreadNudgeDelayMin(delay: Int) {
        viewModelScope.launch { settingsRepository.setUnreadNudgeDelayMin(delay) }
    }

    fun setReviewDigestEnabled(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReviewDigestEnabled(enabled)
            com.reater.app.notify.NotifyCenter.rescheduleReviewDigest(
                context.applicationContext, enabled, reviewDigestHour.value
            )
        }
    }

    fun setReviewDigestHour(context: android.content.Context, hour: Int) {
        viewModelScope.launch {
            settingsRepository.setReviewDigestHour(hour)
            if (reviewDigestEnabled.value) {
                com.reater.app.notify.NotifyCenter.rescheduleReviewDigest(
                    context.applicationContext, true, hour
                )
            }
        }
    }

    fun moveToTrash(item: ItemDetail) {
        viewModelScope.launch {
            repository.moveToTrash(item.item.id)
        }
    }

    fun restoreFromTrash(item: ItemDetail) {
        viewModelScope.launch {
            repository.restoreFromTrash(item.item.id)
        }
    }

    fun deletePermanently(item: ItemDetail) {
        viewModelScope.launch {
            repository.deletePost(item.item.id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    suspend fun unlockWithPasscode(code: String, email: String): Boolean {
        return settingsRepository.unlockProWithPasscode(code, email)
    }

    fun buyPro(activity: android.app.Activity) = billingManager.launchPurchase(activity)
    fun restorePro() = billingManager.refreshPurchases()

    suspend fun exportJson(output: java.io.OutputStream) = backupManager.exportToJson(output)
    suspend fun exportCsv(output: java.io.OutputStream) = backupManager.exportToCsv(output)
    suspend fun importJson(input: java.io.InputStream) = backupManager.importFromJson(input)

    fun saveAiConfig(apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            if (apiKey.isNotBlank()) settingsRepository.setOpenAiApiKey(apiKey)
            settingsRepository.setCustomBaseUrl(baseUrl)
            settingsRepository.setSelectedModel(model)
        }
    }

    fun setAiTransmissionConsent(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiTransmissionConsent(enabled) }
    }

    suspend fun getSavedApiKey(): String? {
        return settingsRepository.getOpenAiApiKey()
    }

    suspend fun summarizeItemWithAi(item: ItemDetail): Result<String> {
        val key = settingsRepository.getOpenAiApiKey().orEmpty()
        if (key.isBlank()) return Result.failure(IllegalStateException("請先於設定中配置 API Key"))

        val commentsJoined = item.comments.joinToString("\n") { "${it.author}: ${it.text}" }
        val res = openAiClient.analyzePost(
            apiKey = key,
            model = selectedModel.value,
            customBaseUrl = customBaseUrl.value,
            postContent = item.displayBody,
            commentsContent = commentsJoined
        )

        return res.mapCatching { (analysis, usage) ->
            val summaryText = analysis.summary
            repository.persistAiAnalysis(
                itemId = item.item.id,
                analysis = analysis,
                usage = usage,
                model = selectedModel.value,
                inputHash = openAiClient.computeInputHash(item.displayBody + "\n" + commentsJoined),
                promptVersion = "v1"
            )
            summaryText
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val allPosts by viewModel.allPosts.collectAsState()
    val unreadPosts by viewModel.unreadPosts.collectAsState()
    val favoritePosts by viewModel.favoritePosts.collectAsState()
    val trashPosts by viewModel.trashPosts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTab by viewModel.tabIndex.collectAsState()
    val isPro by viewModel.isProUnlocked.collectAsState()
    val collections by viewModel.smartCollections.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val proCategoryFilter by viewModel.proCategoryFilter.collectAsState()
    val proFilteredPosts = remember(posts, proCategoryFilter, searchQuery, allPosts) {
        val base = if (searchQuery.isNotBlank()) posts else allPosts
        when (proCategoryFilter) {
            null -> base
            -1L -> base.filter { it.userEdit?.categoryId == null }
            else -> base.filter { it.userEdit?.categoryId == proCategoryFilter }
        }
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val ioScope = androidx.compose.runtime.rememberCoroutineScope()

    val customAvatarId by viewModel.customAvatarId.collectAsState()
    val customAvatarUri by viewModel.customAvatarUri.collectAsState()
    var showIconGallery by remember { mutableStateOf(false) }

    val reaterExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) ioScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { viewModel.exportJson(it) }
                        ?: error("無法開啟指定匯出檔案")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "已匯出 .reater 備份檔" else "匯出失敗：${result.exceptionOrNull()?.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val reaterImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ioScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 自動過濾：僅接受 .reater 專屬格式（檔名 + 內容雙重校驗，詳見 BackupManager）
                    val displayName = runCatching {
                        var name: String? = null
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
                        }
                        name
                    }.getOrNull()
                    if (displayName != null &&
                        !displayName.endsWith(".reater", ignoreCase = true) &&
                        !displayName.endsWith(".json", ignoreCase = true)
                    ) {
                        error("僅支援 .reater 專屬格式，請重新選擇 .reater 備份檔")
                    }
                    context.contentResolver.openInputStream(uri)?.use { viewModel.importJson(it).getOrThrow() }
                        ?: error("無法開啟所選備份檔案")
                }
            }
            Toast.makeText(
                context,
                result.fold({ "已還原備份（共 $it 筆）" }, { "還原失敗：${it.localizedMessage}" }),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var selectedItemForDetail by remember { mutableStateOf<ItemDetail?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ItemDetail?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    // Deep Link 帶入的預填值（一鍵開通失敗時讓用戶直接按驗證重試）
    var unlockPrefillEmail by remember { mutableStateOf("") }
    var unlockPrefillCode by remember { mutableStateOf("") }

    // Pro 一鍵開通：reater://pro-unlock?email=..&code=.. 點開即自動驗證解鎖
    val deepLink by MainActivity.proDeepLinkFlow.collectAsState()
    LaunchedEffect(deepLink) {
        val pending = deepLink ?: return@LaunchedEffect
        MainActivity.proDeepLinkFlow.value = null
        unlockPrefillEmail = pending.email
        unlockPrefillCode = pending.code
        val success = viewModel.unlockWithPasscode(pending.code, pending.email)
        Toast.makeText(
            context,
            if (success) "Reater Pro 已成功解鎖！"
            else "一鍵開通失敗，已帶入啟用碼，請按「驗證並啟用」重試",
            Toast.LENGTH_LONG
        ).show()
        if (!success) showUnlockDialog = true
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("移至垃圾桶？") },
            text = { Text("此記錄將移入垃圾桶並保留 30 天，期間內可隨時還原；超過 30 天後系統將自動永久清除。") },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete?.let { viewModel.moveToTrash(it) }
                        itemToDelete = null
                        Toast.makeText(context, "已移至垃圾桶 (保留 30 天)", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("移至垃圾桶")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("清空垃圾桶？") },
            text = { Text("確定要永久刪除垃圾桶中的所有記錄嗎？此動作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyTrashConfirm = false
                        Toast.makeText(context, "垃圾桶已清空", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (selectedItemForDetail != null) {
        // 以最新 Flow 資料解析，改分類 / AI 摘要後徽章與內容即時更新
        val liveDetail = posts.firstOrNull { it.item.id == selectedItemForDetail!!.item.id }
            ?: selectedItemForDetail!!
        DetailDialog(
            item = liveDetail,
            categories = categories,
            viewModel = viewModel,
            onDismiss = {
                com.reater.app.ui.player.VideoPlaybackManager.pauseAll()
                selectedItemForDetail = null
            },
            onMoveToTrash = {
                com.reater.app.ui.player.VideoPlaybackManager.pauseAll()
                viewModel.moveToTrash(liveDetail)
                selectedItemForDetail = null
                Toast.makeText(context, "已移至垃圾桶", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onExportJson = {
                val defaultName = "reater_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())}.reater"
                reaterExportLauncher.launch(defaultName)
            },
            onImportJson = { reaterImportLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            onOpenIconGallery = { showIconGallery = true },
            onOpenUnlock = { showUnlockDialog = true }
        )
    }

    if (showIconGallery) {
        IconGalleryDialog(
            isPro = isPro,
            currentAvatarId = customAvatarId,
            onSelectAvatar = { id ->
                viewModel.setCustomAvatarId(id)
                Toast.makeText(context, "已成功更換個人頭貼", Toast.LENGTH_SHORT).show()
                showIconGallery = false
            },
            onOpenUnlock = {
                showIconGallery = false
                showUnlockDialog = true
            },
            onDismiss = { showIconGallery = false }
        )
    }

    if (showUnlockDialog) {
        PasscodeUnlockDialog(
            viewModel = viewModel,
            onDismiss = { showUnlockDialog = false },
            initialEmail = unlockPrefillEmail,
            initialCode = unlockPrefillCode
        )
    }

    // PRO Tab 建分類 Dialog（與分享頁同規格：免費 3 上限、圖示 8 精確款）
    val showCreateCat by viewModel.showCreateCategoryDialog.collectAsState()
    val createPrefill by viewModel.createCategoryPrefill.collectAsState()
    if (showCreateCat) {
        var catName by remember(createPrefill) { mutableStateOf(createPrefill) }
        var selectedAvatar by remember { mutableStateOf("life") }
        Dialog(onDismissRequest = { viewModel.setShowCreateCategoryDialog(false) }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    com.reater.app.ui.components.DialogHeader(
                        title = "新建分類",
                        onClose = { viewModel.setShowCreateCategoryDialog(false) },
                        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .dismissFocusOnTap(focusManager)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                    run {
                        val customCount = categories.count { !it.isDefault }
                        if (!isPro) {
                            val remaining = com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT - customCount
                            Text(
                                text = if (remaining > 0) "免費版還可新增 $remaining 個自訂分類（已用 $customCount/3）"
                                else "免費版自訂分類已滿（3/3），升級 Pro 可無限新增",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        placeholder = { Text("分類名稱（如：技術、生活、閱讀）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "選擇圖示", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.reater.app.ui.AvatarIcons.ALL.forEach { item ->
                            val isSelected = selectedAvatar == item.id
                            val isLocked = item.isPro && !isPro
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else if (item.isPro) Color(0xFFFFB300).copy(alpha = 0.5f)
                                        else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (isLocked) {
                                            showUnlockDialog = true
                                        } else {
                                            selectedAvatar = item.id
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = item.resId),
                                    contentDescription = item.name,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                                if (isLocked) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Pro 專屬",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { viewModel.setShowCreateCategoryDialog(false) }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.createCategory(catName, selectedAvatar) },
                            enabled = catName.isNotBlank()
                        ) {
                            Text("建立")
                        }
                    }
                    }
                }
            }
        }
    }

    // PRO 配額滿額提示（與 ProCopy 同文案；確認即轉解鎖）
    val showLimit by viewModel.showProLimitNotice.collectAsState()
    if (showLimit) {
        Dialog(onDismissRequest = { viewModel.setShowProLimitNotice(false) }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    com.reater.app.ui.components.DialogHeader(
                        title = com.reater.app.ui.components.ProCopy.SHARE_LIMIT_TITLE,
                        onClose = { viewModel.setShowProLimitNotice(false) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = com.reater.app.ui.components.ProCopy.SHARE_LIMIT_DESC,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { viewModel.setShowProLimitNotice(false) }) {
                            Text("我知道了")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.setShowProLimitNotice(false)
                            showUnlockDialog = true
                        }) {
                            Text("解鎖 Pro")
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showIconGallery = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (isPro) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFB300), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val activePosts = if (currentTab == 3) proFilteredPosts else posts
                        if (activePosts.isNotEmpty()) {
                            val shareText = activePosts.joinToString("\n") { detail ->
                                formatPostShareText(detail)
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Reater Group Share", shareText))
                            Toast.makeText(context, "已複製分組共 ${activePosts.size} 則網址與摘要", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "目前分組無貼文", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "複製分組所有網址與摘要",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (isPro) {
                        IconButton(onClick = { showIconGallery = true }) {
                            UserAvatarView(
                                avatarId = customAvatarId,
                                avatarUri = customAvatarUri,
                                size = 32.dp
                            )
                        }
                    } else {
                        IconButton(onClick = { showUnlockDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "解鎖 Pro",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissFocusOnTap(focusManager)
        ) {
            // Search Bar（Search 鍵同樣收合鍵盤，有內容時顯示 X 清除按鈕）
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = {
                    Text(
                        stringResource(R.string.search_hint),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.onSearchQueryChanged("")
                                focusManager.clearFocus()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除搜尋",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = currentTab.coerceIn(0, 5),
                pageCount = { 6 }
            )
            val pagerScope = androidx.compose.runtime.rememberCoroutineScope()

            LaunchedEffect(pagerState.currentPage) {
                if (currentTab != pagerState.currentPage) {
                    viewModel.onTabChanged(pagerState.currentPage)
                }
                if (pagerState.currentPage == 5) {
                    viewModel.loadAnalytics()
                }
            }

            LaunchedEffect(currentTab) {
                if (pagerState.currentPage != currentTab) {
                    pagerState.animateScrollToPage(currentTab)
                }
            }

            // Tab 標籤列：點擊切換；左右滑動切換由下方 HorizontalPager 跟手處理
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.tab_all)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.tab_unread)) }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.tab_favorite)) }
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(3) } },
                    text = { Text("PRO 分類") }
                )
                Tab(
                    selected = pagerState.currentPage == 4,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(4) } },
                    text = { Text("垃圾桶 (30天)") }
                )
                Tab(
                    selected = pagerState.currentPage == 5,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(5) } },
                    text = { Text("分析") }
                )
            }

            // 支援主畫面內容左右滑動切換頁面
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        val displayList = if (searchQuery.isNotBlank()) posts else allPosts
                        PostListTab(
                            posts = displayList,
                            categories = categories,
                            emptyMessage = if (searchQuery.isNotBlank()) "找不到符合「$searchQuery」的內容"
                            else "目前尚無內容\n在 Threads 中點擊分享至 Reater 即可保存！",
                            onSelectDetail = { detail ->
                                selectedItemForDetail = detail
                                viewModel.markAsRead(detail)
                                viewModel.recordOpen(detail)
                            },
                            onToggleRead = { detail ->
                                val willBeRead = !detail.isRead
                                viewModel.toggleRead(detail)
                                Toast.makeText(
                                    context,
                                    if (willBeRead) "已標為已讀（不再顯示於未讀）" else "已標為未讀",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = { detail ->
                                val willFav = !detail.isFavorite
                                viewModel.toggleFavorite(detail)
                                Toast.makeText(
                                    context,
                                    if (willFav) "已加入收藏" else "已取消收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = { itemToDelete = it }
                        )
                    }
                    1 -> {
                        val displayList = if (searchQuery.isNotBlank()) posts.filter { !it.isRead } else unreadPosts
                        PostListTab(
                            posts = displayList,
                            categories = categories,
                            emptyMessage = if (searchQuery.isNotBlank()) "未讀中找不到符合「$searchQuery」的內容"
                            else "太棒了！所有貼文皆已閱讀完畢",
                            onSelectDetail = { detail ->
                                selectedItemForDetail = detail
                                viewModel.markAsRead(detail)
                                viewModel.recordOpen(detail)
                            },
                            onToggleRead = { detail ->
                                val willBeRead = !detail.isRead
                                viewModel.toggleRead(detail)
                                Toast.makeText(
                                    context,
                                    if (willBeRead) "已標為已讀（不再顯示於未讀）" else "已標為未讀",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = { detail ->
                                val willFav = !detail.isFavorite
                                viewModel.toggleFavorite(detail)
                                Toast.makeText(
                                    context,
                                    if (willFav) "已加入收藏" else "已取消收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = { itemToDelete = it }
                        )
                    }
                    2 -> {
                        val displayList = if (searchQuery.isNotBlank()) posts.filter { it.isFavorite } else favoritePosts
                        PostListTab(
                            posts = displayList,
                            categories = categories,
                            emptyMessage = if (searchQuery.isNotBlank()) "收藏中找不到符合「$searchQuery」的內容"
                            else "尚無收藏貼文\n在貼文卡片點擊書籤即可收藏！",
                            onSelectDetail = { detail ->
                                selectedItemForDetail = detail
                                viewModel.markAsRead(detail)
                                viewModel.recordOpen(detail)
                            },
                            onToggleRead = { detail ->
                                val willBeRead = !detail.isRead
                                viewModel.toggleRead(detail)
                                Toast.makeText(
                                    context,
                                    if (willBeRead) "已標為已讀（不再顯示於未讀）" else "已標為未讀",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = { detail ->
                                val willFav = !detail.isFavorite
                                viewModel.toggleFavorite(detail)
                                Toast.makeText(
                                    context,
                                    if (willFav) "已加入收藏" else "已取消收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = { itemToDelete = it }
                        )
                    }
                    3 -> {
                        ProCategoryTabContent(
                            categories = categories,
                            isPro = isPro,
                            proCategoryFilter = proCategoryFilter,
                            onSelectCategoryFilter = { viewModel.setProCategoryFilter(it) },
                            onAddCategoryClick = {
                                val customCount = categories.count { !it.isDefault }
                                if (!isPro && customCount >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT) {
                                    showUnlockDialog = true
                                } else {
                                    viewModel.setShowCreateCategoryDialog(true)
                                }
                            },
                            collections = collections,
                            posts = proFilteredPosts,
                            onSelectDetail = { detail ->
                                selectedItemForDetail = detail
                                viewModel.markAsRead(detail)
                                viewModel.recordOpen(detail)
                            },
                            onToggleRead = { detail ->
                                val willBeRead = !detail.isRead
                                viewModel.toggleRead(detail)
                                Toast.makeText(
                                    context,
                                    if (willBeRead) "已標為已讀（不再顯示於未讀）" else "已標為未讀",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = { detail ->
                                val willFav = !detail.isFavorite
                                viewModel.toggleFavorite(detail)
                                Toast.makeText(
                                    context,
                                    if (willFav) "已加入收藏" else "已取消收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = { itemToDelete = it },
                            onOpenUnlock = { showUnlockDialog = true }
                        )
                    }
                    4 -> {
                        TrashTabContent(
                            trashPosts = trashPosts,
                            onEmptyTrash = { showEmptyTrashConfirm = true },
                            onRestore = { detail ->
                                viewModel.restoreFromTrash(detail)
                                Toast.makeText(context, "已成功還原！", Toast.LENGTH_SHORT).show()
                            },
                            onDeletePermanently = { detail ->
                                viewModel.deletePermanently(detail)
                                Toast.makeText(context, "已永久刪除", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    5 -> {
                        AnalyticsScreen(
                            viewModel = viewModel,
                            isPro = isPro,
                            onOpenUnlock = { showUnlockDialog = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostListTab(
    posts: List<ItemDetail>,
    categories: List<CategoryEntity>,
    emptyMessage: String,
    onSelectDetail: (ItemDetail) -> Unit,
    onToggleRead: (ItemDetail) -> Unit,
    onToggleFavorite: (ItemDetail) -> Unit,
    onDelete: (ItemDetail) -> Unit
) {
    if (posts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(posts, key = { it.item.id }) { itemDetail ->
                PostCard(
                    itemDetail = itemDetail,
                    categories = categories,
                    onClick = { onSelectDetail(itemDetail) },
                    onToggleRead = { onToggleRead(itemDetail) },
                    onToggleFavorite = { onToggleFavorite(itemDetail) },
                    onDelete = { onDelete(itemDetail) }
                )
            }
        }
    }
}

@Composable
private fun ProCategoryTabContent(
    categories: List<CategoryEntity>,
    isPro: Boolean,
    proCategoryFilter: Long?,
    onSelectCategoryFilter: (Long?) -> Unit,
    onAddCategoryClick: () -> Unit,
    collections: List<SavedCollectionEntity>,
    posts: List<ItemDetail>,
    onSelectDetail: (ItemDetail) -> Unit,
    onToggleRead: (ItemDetail) -> Unit,
    onToggleFavorite: (ItemDetail) -> Unit,
    onDelete: (ItemDetail) -> Unit,
    onOpenUnlock: () -> Unit
) {
    val customCount = categories.count { !it.isDefault }
    val quotaText = if (isPro) "分類無上限・共 ${categories.size} 個"
    else "免費 $customCount/3・升級無上限"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "我的分類",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = quotaText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (categories.isEmpty()) {
                Text(
                    text = "尚無自訂分類，按下方 + 新增（免費可建 3 個）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 19.sp
                )
            } else {
                com.reater.app.ui.components.ProCategoryFilterRow(
                    categories = categories,
                    selectedId = proCategoryFilter,
                    onSelect = onSelectCategoryFilter
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddCategoryClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (!isPro && customCount >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT)
                        "+ 新增分類（升級 Pro 無上限）"
                    else "+ 新增分類"
                )
            }
        }
        if (isPro && collections.isNotEmpty()) {
            item {
                Text(
                    text = "您的智慧篩選條件",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
            }
            items(collections, key = { it.id }) { col ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = col.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "規則生效中",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "篩選結果（共 ${posts.size} 筆）",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (posts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "此分類目前尚無內容",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(posts, key = { it.item.id }) { itemDetail ->
                PostCard(
                    itemDetail = itemDetail,
                    categories = categories,
                    onClick = { onSelectDetail(itemDetail) },
                    onToggleRead = { onToggleRead(itemDetail) },
                    onToggleFavorite = { onToggleFavorite(itemDetail) },
                    onDelete = { onDelete(itemDetail) }
                )
            }
        }
        if (!isPro) {
            item {
                ProUnlockCard(onUnlockClick = onOpenUnlock)
            }
        }
    }
}

@Composable
private fun TrashTabContent(
    trashPosts: List<ItemDetail>,
    onEmptyTrash: () -> Unit,
    onRestore: (ItemDetail) -> Unit,
    onDeletePermanently: (ItemDetail) -> Unit
) {
    if (trashPosts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "垃圾桶目前是空的\n被移除的項目會在此保留 30 天",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "垃圾桶記錄 (${trashPosts.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    OutlinedButton(
                        onClick = onEmptyTrash,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清空垃圾桶", fontSize = 12.sp)
                    }
                }
            }
            items(trashPosts, key = { it.item.id }) { itemDetail ->
                TrashCard(
                    itemDetail = itemDetail,
                    onRestore = { onRestore(itemDetail) },
                    onDeletePermanently = { onDeletePermanently(itemDetail) }
                )
            }
        }
    }
}


@Composable
fun TrashCard(
    itemDetail: ItemDetail,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val deletedTime = itemDetail.item.deletedAt ?: System.currentTimeMillis()
    val elapsedDays = ((System.currentTimeMillis() - deletedTime) / (1000L * 60 * 60 * 24)).toInt()
    val remainingDays = maxOf(0, 30 - elapsedDays)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${itemDetail.item.authorHandle.ifBlank { "threads_user" }}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "剩餘 ${remainingDays} 天後永久清除",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = remember(itemDetail.displayBody) {
                    itemDetail.displayBody.trim()
                        .replace(Regex("\n{3,}"), "\n\n")
                        .ifBlank { "無內文" }
                },
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("還原", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDeletePermanently,
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("永久刪除", fontSize = 12.sp)
                }
            }
        }
    }
}

