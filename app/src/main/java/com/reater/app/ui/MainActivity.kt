package com.reater.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        setContent {
            MainThemedScreen()
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
        val dailySaved: List<Pair<String, Int>> = emptyList()
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
                dailySaved = snap.dailySaved
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
    val trashPosts by viewModel.trashPosts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTab by viewModel.tabIndex.collectAsState()
    val isPro by viewModel.isProUnlocked.collectAsState()
    val collections by viewModel.smartCollections.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val proCategoryFilter by viewModel.proCategoryFilter.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val ioScope = androidx.compose.runtime.rememberCoroutineScope()

    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) ioScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { viewModel.exportJson(it) }
                        ?: error("Cannot open the selected export file")
                }
            }
            Toast.makeText(context, if (result.isSuccess) "JSON 匯出完成" else "JSON 匯出失敗：${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ioScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { viewModel.importJson(it).getOrThrow() }
                        ?: error("Cannot open the selected backup file")
                }
            }
            Toast.makeText(context, result.fold({ "已匯入 $it 筆資料" }, { "JSON 匯入失敗：${it.localizedMessage}" }), Toast.LENGTH_LONG).show()
        }
    }

    var selectedItemForDetail by remember { mutableStateOf<ItemDetail?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ItemDetail?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

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
                    Text("確定清空")
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
            onDismiss = { selectedItemForDetail = null },
            onMoveToTrash = {
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
            onExportJson = { jsonExportLauncher.launch("reater-backup.json") },
            onImportJson = { jsonImportLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }
        )
    }

    if (showUnlockDialog) {
        PasscodeUnlockDialog(
            viewModel = viewModel,
            onDismiss = { showUnlockDialog = false }
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
                        singleLine = true
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAvatar = item.id },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = item.resId),
                                    contentDescription = item.name,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (isPro) {
                            showSettingsDialog = true
                        } else {
                            showUnlockDialog = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isPro) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Pro Status",
                            tint = if (isPro) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        // 點擊外部空白 + 滑動即收合搜尋鍵盤（pointerInput 不擋捲動）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissFocusOnTap(focusManager)
        ) {
            // Search Bar（Search 鍵同樣收合鍵盤）
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            // Scrollable Tabs so text never wraps or squishes
            ScrollableTabRow(
                selectedTabIndex = currentTab,
                edgePadding = 16.dp
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { viewModel.onTabChanged(0) },
                    text = { Text(stringResource(R.string.tab_all)) }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { viewModel.onTabChanged(1) },
                    text = { Text(stringResource(R.string.tab_unread)) }
                )
                Tab(
                    selected = currentTab == 2,
                    onClick = { viewModel.onTabChanged(2) },
                    text = { Text(stringResource(R.string.tab_favorite)) }
                )
                Tab(
                    selected = currentTab == 3,
                    onClick = { viewModel.onTabChanged(3) },
                    text = { Text(if (isPro) "PRO 分類" else "PRO 分類") }
                )
                Tab(
                    selected = currentTab == 4,
                    onClick = { viewModel.onTabChanged(4) },
                    text = { Text("垃圾桶 (30天)") }
                )
                Tab(
                    selected = currentTab == 5,
                    onClick = { viewModel.onTabChanged(5) },
                    text = { Text("分析") }
                )
            }

            // Tab 3: PRO 分類（免費預覽 3 自訂、Pro 無上限；+ 與底部解鎖 card 觸發解鎖）
            if (currentTab == 3) {
                val customCount = categories.count { !it.isDefault }
                val quotaText = if (isPro) "分類無上限・共 ${categories.size} 個"
                else "免費 $customCount/3・升級無上限"
                val proFilteredPosts = remember(posts, proCategoryFilter) {
                    when (proCategoryFilter) {
                        null -> posts
                        -1L -> posts.filter { it.userEdit?.categoryId == null }
                        else -> posts.filter { it.userEdit?.categoryId == proCategoryFilter }
                    }
                }
                fun onAddCategory() {
                    if (!isPro && customCount >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT) {
                        showUnlockDialog = true
                    } else {
                        viewModel.setShowCreateCategoryDialog(true)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                                onSelect = { viewModel.setProCategoryFilter(it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onAddCategory() },
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
                            text = "篩選結果（共 ${proFilteredPosts.size} 筆）",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (proFilteredPosts.isEmpty()) {
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
                        items(proFilteredPosts, key = { it.item.id }) { itemDetail ->
                            PostCard(
                                itemDetail = itemDetail,
                                categories = categories,
                                onClick = {
                                    selectedItemForDetail = itemDetail
                                    viewModel.markAsRead(itemDetail)
                                    viewModel.recordOpen(itemDetail)
                                },
                                onToggleRead = {
                                    val willBeRead = !itemDetail.isRead
                                    viewModel.toggleRead(itemDetail)
                                    Toast.makeText(
                                        context,
                                        if (willBeRead) "已標為已讀（不再顯示於未讀）" else "已標為未讀",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onToggleFavorite = {
                                    val willFav = !itemDetail.isFavorite
                                    viewModel.toggleFavorite(itemDetail)
                                    Toast.makeText(
                                        context,
                                        if (willFav) "已加入收藏" else "已取消收藏",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onDelete = { itemToDelete = itemDetail }
                            )
                        }
                    }
                    // 底部解鎖 card（免費版）：升級 Pro 無上限分類
                    if (!isPro) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF8E1)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = com.reater.app.ui.components.ProCopy.LOCK_TITLE,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = com.reater.app.ui.components.ProCopy.LOCK_DESC_UNLOCKED_FEATURES,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        lineHeight = 19.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = { showUnlockDialog = true }) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(com.reater.app.ui.components.ProCopy.LOCK_CTA)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (currentTab == 4) {
                // Tab 4: Trash view (30-day retention)
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                                    onClick = { showEmptyTrashConfirm = true },
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
                                onRestore = {
                                    viewModel.restoreFromTrash(itemDetail)
                                    Toast.makeText(context, "已成功還原！", Toast.LENGTH_SHORT).show()
                                },
                                onDeletePermanently = {
                                    viewModel.deletePermanently(itemDetail)
                                    Toast.makeText(context, "已永久刪除", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            } else if (currentTab == 5) {
                // Tab 5: 分析（今日儲存 / 今日打開 / 累計回顧；免費當週、Pro 近30天）
                AnalyticsScreen(
                    viewModel = viewModel,
                    isPro = isPro,
                    onOpenUnlock = { showUnlockDialog = true }
                )
            } else if (posts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "目前尚無內容\n在 Threads 中點擊分享至 Reater 即可保存！",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(posts, key = { it.item.id }) { itemDetail ->
                        PostCard(
                            itemDetail = itemDetail,
                            categories = categories,
                            onClick = {
                                selectedItemForDetail = itemDetail
                                viewModel.markAsRead(itemDetail)
                                viewModel.recordOpen(itemDetail)
                            },
                            onToggleRead = {
                                val willBeRead = !itemDetail.isRead
                                viewModel.toggleRead(itemDetail)
                                Toast.makeText(
                                    context,
                                    if (willBeRead) "已標為已讀（不再顯示於未讀）" else "已標為未讀",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = {
                                val willFav = !itemDetail.isFavorite
                                viewModel.toggleFavorite(itemDetail)
                                Toast.makeText(
                                    context,
                                    if (willFav) "已加入收藏" else "已取消收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = { itemToDelete = itemDetail }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    itemDetail: ItemDetail,
    categories: List<CategoryEntity>,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val category = categories.firstOrNull { it.id == itemDetail.userEdit?.categoryId }
    val firstMedia = itemDetail.media.firstOrNull()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (itemDetail.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (itemDetail.isRead) 1.dp else 2.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Author, Category Badge, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(
                        text = "@${itemDetail.item.authorHandle.ifBlank { "threads_user" }}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (itemDetail.item.authorVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (category != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = AvatarIcons.getDrawableRes(category.avatarIcon)),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = category.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Open in external browser
                    IconButton(
                        onClick = {
                            runCatching {
                                val url = itemDetail.item.canonicalUrl
                                if (url.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "外部開啟",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Read Toggle
                    IconButton(
                        onClick = onToggleRead,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (itemDetail.isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (itemDetail.isRead) "已讀" else "未讀",
                            tint = if (itemDetail.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Favorite Toggle
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (itemDetail.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (itemDetail.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move to Trash
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "移至垃圾桶",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body Text — 點內文即展開詳情（像 Threads）；URL 仍可點擊外部跳轉
            com.reater.app.ui.components.LinkifiedText(
                text = remember(itemDetail.displayBody) {
                    itemDetail.displayBody.trim()
                        .replace(Regex("\n{3,}"), "\n\n")
                        .ifBlank { "無內文" }
                },
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                onNeutralClick = onClick
            )

            // 未展開卡片：圖片縮圖 + 影片直接顯示播放器；點圖片/影片/內文皆展開詳情
            if (firstMedia != null) {
                Spacer(modifier = Modifier.height(10.dp))
                if (firstMedia.kind.equals("IMAGE", ignoreCase = true)) {
                    val imageSource = if (firstMedia.localPath.isNotBlank() && File(firstMedia.localPath).exists()) {
                        File(firstMedia.localPath)
                    } else {
                        firstMedia.remoteUrl
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onClick)
                    ) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = "Post Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    // 影片未展開即顯示播放預覽；整塊點擊展開詳情（像 Threads），詳情內可橫向全螢幕
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        com.reater.app.ui.player.InlineVideoPlayer(
                            remoteUrl = firstMedia.remoteUrl,
                            localPath = firstMedia.localPath,
                            modifier = Modifier.fillMaxSize()
                        )
                        // 透明覆蓋層：攔截點擊展開詳情（播放器本身的點播/暫停留給詳情頁）
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(onClick = onClick)
                        )
                        // 右下提示：點按展開
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("點按展開詳情", fontSize = 11.sp, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }

            if (itemDetail.manualSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "✨ AI 摘要: ${itemDetail.manualSummary}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (itemDetail.manualNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "筆記: ${itemDetail.manualNote}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (itemDetail.comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "包含 ${itemDetail.comments.size} 則精華留言",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 儲存時間記錄
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = com.reater.app.ui.components.formatSavedTime(itemDetail.item.sourceFetchedAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
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

@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    isPro: Boolean,
    onOpenUnlock: () -> Unit
) {
    val state by viewModel.analytics.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    if (state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "今日儲存",
                    value = state.savedToday.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "今日打開",
                    value = state.openedToday.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "累計回顧",
                    value = state.totalReviews.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Text(
                text = if (isPro) "近 30 天儲存趨勢" else "本週儲存趨勢",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPro) "Pro 可查看無期限完整趨勢"
                else "免費版僅顯示當週・升級 Pro 查看 30 天趨勢",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (state.dailySaved.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "期間內尚無儲存記錄",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            item {
                // X 軸=日期、Y 軸=次數：直條 + 折線複合圖
                AnalyticsBarLineChart(
                    data = state.dailySaved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(vertical = 4.dp)
                )
            }
        }
        if (!isPro) {
            item {
                OutlinedButton(
                    onClick = onOpenUnlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("解鎖 Pro・趨勢無期限查看")
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 分析頁複合圖：X 軸=日期、Y 軸=次數，直條 + 折線。
 */
@Composable
private fun AnalyticsBarLineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)
        ) {
            Text(text = "次數", fontSize = 11.sp, color = outline)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                // Y 軸數字（0 / max/2 / max）
                val maxV = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                Column(
                    modifier = Modifier.height(150.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = maxV.toString(), fontSize = 10.sp, color = outline)
                    Text(text = (maxV / 2).toString(), fontSize = 10.sp, color = outline)
                    Text(text = "0", fontSize = 10.sp, color = outline)
                }
                Spacer(modifier = Modifier.width(6.dp))
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.weight(1f).height(150.dp)
                ) {
                    if (data.isEmpty()) return@Canvas
                    val max = maxV.toFloat()
                    val n = data.size
                    val barW = (size.width / (n * 1.6f)).coerceAtLeast(8f)
                    val stepX = size.width / n.coerceAtLeast(1)
                    for (g in 0..2) {
                        val y = size.height - (size.height * 0.9f * g / 2f) - size.height * 0.02f
                        drawLine(
                            color = surfaceVariant,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }
                    data.forEachIndexed { i, (_, cnt) ->
                        val h = (size.height * 0.9f * cnt / max).coerceAtLeast(if (cnt > 0) 6f else 0f)
                        val x = stepX * i + (stepX - barW) / 2f
                        val y = size.height * 0.98f - h
                        drawRoundRect(
                            color = primary.copy(alpha = 0.85f),
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barW, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                    }
                    if (n > 1) {
                        val pts = data.mapIndexed { i, (_, cnt) ->
                            androidx.compose.ui.geometry.Offset(
                                stepX * i + stepX / 2f,
                                size.height * 0.98f - (size.height * 0.9f * cnt / max)
                            )
                        }
                        for (i in 0 until pts.size - 1) {
                            drawLine(color = primary, start = pts[i], end = pts[i + 1], strokeWidth = 4f)
                        }
                        pts.forEach { p ->
                            drawCircle(color = primary, radius = 7f, center = p)
                            drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 3.5f, center = p)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // X 軸日期：固定高度 20dp，確保不被卡片底部裁切
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp).padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                val showEvery = when {
                    data.size <= 7 -> 1
                    data.size <= 14 -> 2
                    else -> 4
                }
                data.forEachIndexed { i, (day, _) ->
                    if (i % showEvery == 0 || i == data.size - 1) {
                        Text(
                            text = com.reater.app.ui.components.shortDayLabel(day),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = outline,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "日期",
                fontSize = 11.sp,
                color = outline,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun DetailDialog(
    item: ItemDetail,
    categories: List<CategoryEntity>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onMoveToTrash: () -> Unit
) {
    val context = LocalContext.current
    var isSummarizing by remember { mutableStateOf(false) }
    var currentSummary by remember { mutableStateOf(item.manualSummary) }
    var showAiConsent by remember { mutableStateOf(false) }
    val aiConsent by viewModel.aiTransmissionConsent.collectAsState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val category = categories.firstOrNull { it.id == item.userEdit?.categoryId }
    // 全螢幕媒體檢視器下標（null = 關閉）
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    // 左上 ... 溢位選單 + 改分類編輯器
    var showOverflow by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }

    if (viewerIndex != null) {
        MediaViewerDialog(
            media = item.media.map { ViewerMedia(kind = it.kind, remoteUrl = it.remoteUrl, localPath = it.localPath) },
            startIndex = viewerIndex ?: 0,
            onDismiss = { viewerIndex = null }
        )
    }

    fun runAiSummary() {
        isSummarizing = true
        coroutineScope.launch {
            val result = viewModel.summarizeItemWithAi(item)
            isSummarizing = false
            result.onSuccess {
                currentSummary = it
                Toast.makeText(context, "AI 分析完成！", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "AI 分析失敗", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showAiConsent) {
        Dialog(onDismissRequest = { showAiConsent = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("允許 AI 分析？", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { showAiConsent = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "關閉", modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("按下同意後，這篇貼文內文與已保存的留言會傳送到你設定的 AI 服務端點，用於分類與摘要。Reater 不代收 API Key 或代付 AI 費用。你可以在設定中撤回同意。")
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showAiConsent = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.setAiTransmissionConsent(true)
                            showAiConsent = false
                            runAiSummary()
                        }) { Text("同意並分析") }
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄：左上 ... 溢位選單 + 分類徽章（左）+ X 關閉（最右），不隨捲動離開
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { showOverflow = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多操作",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("改分類標籤") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showOverflow = false
                                    showCategoryEditor = !showCategoryEditor
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("AI 智慧摘要")
                                        Text(
                                            text = "產生摘要・分類建議・標籤",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                },
                                onClick = {
                                    showOverflow = false
                                    if (aiConsent) runAiSummary() else showAiConsent = true
                                }
                            )
                        }
                    }
                    com.reater.app.ui.components.CategoryBadge(
                        category = category,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "關閉",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                // 改分類編輯器（由 ... 選單展開）：下拉篩選 + 即時生效
                if (showCategoryEditor) {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.reater.app.ui.components.CategoryDropdown(
                        categories = categories,
                        selectedCategoryId = item.userEdit?.categoryId,
                        onSelect = {
                            viewModel.updateCategory(item.item.id, it)
                            Toast.makeText(
                                context,
                                if (it == null) "已改為未分類" else "分類已更新",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onRequestCreate = {
                            Toast.makeText(context, "請至 PRO 分類以 + 新增分類", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 作者列（獨立第二列，不與頂欄搶位）+ 儲存時間
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "@${item.item.authorHandle}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.item.authorVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = com.reater.app.ui.components.formatSavedTime(item.item.sourceFetchedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                // External Link Button（單行省略，避免「在 Threads / 外部瀏覽器開啟」被擠成兩行藥丸）
                if (item.item.canonicalUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.item.canonicalUrl))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "在 Threads / 瀏覽器開啟",
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Media Preview（圖片縮圖 / 影片內建播放，點擊全螢幕放大）
                item.media.forEachIndexed { mediaIndex, m ->
                    val imageSource = if (m.localPath.isNotBlank() && File(m.localPath).exists()) {
                        File(m.localPath)
                    } else {
                        m.remoteUrl
                    }
                    val isVideo = m.kind.equals("VIDEO", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(if (isVideo) Modifier else Modifier.clickable { viewerIndex = mediaIndex })
                    ) {
                        if (isVideo) {
                            com.reater.app.ui.player.InlineVideoPlayer(
                                remoteUrl = m.remoteUrl,
                                localPath = m.localPath,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = imageSource,
                                contentDescription = "Media",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { viewerIndex = mediaIndex },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 抓取未完全成功且無媒體時，提示去 Threads 看圖/影
                // （lastFetchStatus == PARTIAL 代表內文/媒體都沒拿到）
                if (item.media.isEmpty() && item.item.lastFetchStatus == "PARTIAL") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "圖片/影片未能自動下載，可點上方按鈕在 Threads 查看原貼文。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Post Body（若存到的是純 URL，顯示 無內文 + URL 小字，避免詳情頁只剩一串連結）
                // 內文 URL 可點擊外部跳轉
                run {
                    val rawBody = item.displayBody.trim()
                    val isUrlOnly = rawBody.startsWith("http", ignoreCase = true) &&
                        rawBody.lines().size == 1 && rawBody.length < 500 &&
                        (rawBody.contains("threads.com") || rawBody.contains("threads.net"))
                    if (isUrlOnly) {
                        Text(
                            text = "無內文",
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        com.reater.app.ui.components.LinkifiedText(
                            text = rawBody,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            softWrap = true,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        com.reater.app.ui.components.LinkifiedText(
                            text = rawBody.ifBlank { "無內文" },
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            softWrap = true,
                            color = if (rawBody.isBlank()) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (currentSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "✨ AI 智能摘要",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentSummary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (item.manualNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "個人筆記", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.manualNote, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                }

                if (item.comments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "精華留言 (${item.comments.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    item.comments.forEach { c ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(text = "@${c.author}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            com.reater.app.ui.components.LinkifiedText(
                                text = c.text,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 底部操作列：方形按鈕、左右均分不再擠壓換行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onMoveToTrash,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("移至垃圾桶", fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = {
                            if (aiConsent) runAiSummary() else showAiConsent = true
                        },
                        enabled = !isSummarizing,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI 摘要", fontSize = 13.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currentBaseUrl by viewModel.customBaseUrl.collectAsState()
    val currentModel by viewModel.selectedModel.collectAsState()
    val aiConsent by viewModel.aiTransmissionConsent.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val nudgeEnabled by viewModel.unreadNudgeEnabled.collectAsState()
    val nudgeDelay by viewModel.unreadNudgeDelayMin.collectAsState()
    val digestEnabled by viewModel.reviewDigestEnabled.collectAsState()
    val digestHour by viewModel.reviewDigestHour.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf(currentBaseUrl) }
    var modelInput by remember { mutableStateOf(currentModel) }
    // 字體預覽本地態：滑動即時更新底部「Reater 閱讀器」預覽，關閉才全域套用
    var previewScale by remember(fontScale) { mutableStateOf(fontScale) }
    fun dismissWithApply() {
        if (previewScale != fontScale) viewModel.setFontScale(previewScale)
        onDismiss()
    }
    // 推播授權後重組刷新狀態顯示
    var permNonce by remember { mutableStateOf(0) }
    val notifGranted = remember(permNonce) {
        com.reater.app.notify.NotifyCenter.canNotify(context)
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permNonce++ }

    Dialog(onDismissRequest = { dismissWithApply() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄：標題 + X 不隨捲動離開
                com.reater.app.ui.components.DialogHeader(
                    title = "系統設定",
                    onClose = { dismissWithApply() },
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

                // ---------- 外觀 ----------
                SettingsSectionTitle("外觀")
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "亮暗模式", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("system" to "跟隨系統", "light" to "淺色", "dark" to "深色").forEach { (id, label) ->
                        FilterChip(
                            selected = themeMode == id,
                            onClick = { viewModel.setThemeMode(id) },
                            label = { Text(label, maxLines = 1, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "字體大小", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${(previewScale * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = previewScale,
                    onValueChange = { previewScale = it },
                    valueRange = 0.85f..1.3f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Reater 閱讀器",
                    fontSize = (14 * previewScale).sp,
                    lineHeight = (20 * previewScale).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ---------- 通知 ----------
                SettingsSectionTitle("通知")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (notifGranted) "推播權限：已授權"
                        else "推播權限：未授權",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                    if (!notifGranted) {
                        OutlinedButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("授權", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "未讀提醒", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "儲存後若仍未讀，延遲推播提醒",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = nudgeEnabled,
                        onCheckedChange = { viewModel.setUnreadNudgeEnabled(context, it) }
                    )
                }
                if (nudgeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "延遲", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.setUnreadNudgeDelayMin(nudgeDelay - 5) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("−", fontSize = 14.sp) }
                        Text(
                            text = "$nudgeDelay 分鐘",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.setUnreadNudgeDelayMin(nudgeDelay + 5) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("+", fontSize = 14.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "每日回顧", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "每天固定時間彙整未讀數",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = digestEnabled,
                        onCheckedChange = { viewModel.setReviewDigestEnabled(context, it) }
                    )
                }
                if (digestEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "時間", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.setReviewDigestHour(context, digestHour - 1) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("−", fontSize = 14.sp) }
                        Text(
                            text = "${digestHour}：00",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.setReviewDigestHour(context, digestHour + 1) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("+", fontSize = 14.sp) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---------- AI 服務 (BYOK) ----------
                SettingsSectionTitle("AI 服務 (BYOK)")
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "API Key（支援 OpenAI / 相容端點）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = { Text("sk-...", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "API Base URL", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    placeholder = {
                        Text(
                            "https://api.openai.com/v1 或自訂代理",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Model ID", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    placeholder = {
                        Text(
                            "gpt-5-nano, deepseek-chat 等",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = aiConsent,
                        onCheckedChange = viewModel::setAiTransmissionConsent
                    )
                    Text(
                        text = "允許將貼文與留言傳送至設定的 AI 服務",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---------- 備份 ----------
                SettingsSectionTitle("備份")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onExportJson,
                        modifier = Modifier.width(120.dp).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("匯出", maxLines = 1, softWrap = false, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onImportJson,
                        modifier = Modifier.width(120.dp).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("匯入", maxLines = 1, softWrap = false, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 底部僅保留 AI 設定儲存（字體關閉才全域套用；關閉由右上 X 承擔）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = {
                        viewModel.saveAiConfig(apiKeyInput, baseUrlInput, modelInput)
                        Toast.makeText(context, "AI 設定已儲存", Toast.LENGTH_SHORT).show()
                        dismissWithApply()
                    }) {
                        Text("儲存 AI 設定")
                    }
                }
            }
        }
    }
}
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun PasscodeUnlockDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val billingState by viewModel.billingUiState.collectAsState()
    val activity = context as? android.app.Activity
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var passcode by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 固定頂欄
                com.reater.app.ui.components.DialogHeader(
                    title = "解鎖 Reater Pro",
                    onClose = onDismiss,
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = com.reater.app.ui.components.ProCopy.UNLOCK_DESC,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("本機密碼 / 啟用碼解鎖", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    placeholder = {
                        Text(
                            "輸入專屬啟用碼 (如 PRO-XXXX-XXXX-XXXX)",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    placeholder = {
                        Text(
                            "申請時的 Email (必填,與啟用碼綁定)",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:jimmy910824@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Reater Pro 啟用碼申請")
                                putExtra(Intent.EXTRA_TEXT, "您好，我想申請 Reater Pro 啟用金鑰檔案 / 密碼。\nEmail: $emailInput")
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure { Toast.makeText(context, "請手動寄信至 jimmy910824@gmail.com", Toast.LENGTH_LONG).show() }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("聯絡作者信箱", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = {
                            isVerifying = true
                            coroutineScope.launch {
                                val success = viewModel.unlockWithPasscode(passcode, emailInput)
                                isVerifying = false
                                if (success) {
                                    Toast.makeText(context, "Reater Pro 已成功解鎖！", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "啟用碼或密碼不正確，請確認後重試或寄信聯繫管理者。", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = passcode.isNotBlank() && !isVerifying,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text("驗證並啟用", maxLines = 1, softWrap = false)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Google Play Purchase Section
                Text(
                    "或透過 Google Play 一次性購買",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "價格：${billingState.price ?: "連線取得中"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.restorePro() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("還原既有購買", maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = {
                            if (activity != null) viewModel.buyPro(activity)
                            else Toast.makeText(context, "無法開啟 Google Play 購買流程。", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Google Play 購買", maxLines = 1, softWrap = false)
                    }
                }
                }
            }
        }
    }
}
