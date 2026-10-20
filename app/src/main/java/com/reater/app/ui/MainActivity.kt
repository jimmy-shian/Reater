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
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
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
import com.reater.app.domain.defaultCollectionDisplayName
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.reater.app.ui.components.dismissFocusOnTap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.reater.app.domain.CollectionRule
import com.reater.app.domain.SmartCollectionEngine
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.withLock
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

    fun openCategory(categoryId: Long?) {
        _proCategoryFilter.value = categoryId ?: -1L
        _tabIndex.value = 0
    }

    private val categoryMoveMutex = kotlinx.coroutines.sync.Mutex()

    fun moveCategory(category: CategoryEntity, direction: Int) {
        viewModelScope.launch {
            categoryMoveMutex.withLock {
                val ordered = categoryDao.getAllCategories().toMutableList()
                val index = ordered.indexOfFirst { it.id == category.id }
                val target = index + direction
                if (index < 0 || target !in ordered.indices) return@withLock
                java.util.Collections.swap(ordered, index, target)
                categoryDao.reorderCategories(ordered.map { it.id })
            }
        }
    }

    fun reorderAllCategories(newOrderedCategories: List<CategoryEntity>) {
        viewModelScope.launch {
            categoryMoveMutex.withLock {
                categoryDao.reorderCategories(newOrderedCategories.map { it.id })
            }
        }
    }

    fun updateCollectionRule(collection: SavedCollectionEntity, rule: CollectionRule) {
        viewModelScope.launch { smartCollectionEngine.updateRule(collection, rule) }
    }

    fun updateCollection(collection: SavedCollectionEntity, newName: String, newIcon: String, rule: CollectionRule) {
        viewModelScope.launch {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val updated = collection.copy(
                name = newName,
                iconName = newIcon,
                isEnabled = rule.enabled,
                rulesJson = json.encodeToString(CollectionRule.serializer(), rule)
            )
            proDao.insertCollection(updated)
        }
    }

    fun deleteCollection(collection: SavedCollectionEntity) {
        viewModelScope.launch {
            proDao.deleteCollection(collection.id)
        }
    }

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

    val keepDataAfterUninstall: StateFlow<Boolean> = settingsRepository.keepDataAfterUninstall
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
            settingsRepository.isProUnlocked.collect { pro ->
                if (pro) smartCollectionEngine.seedDefaultProCollectionsIfEmpty()
            }
        }
        viewModelScope.launch {
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

    // 分類編輯狀態：null = 未開啟編輯；非 null = 正在編輯該分類
    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)
    val editingCategory: StateFlow<CategoryEntity?> = _editingCategory

    fun setEditingCategory(category: CategoryEntity?) {
        _editingCategory.value = category
    }

    fun renameCategory(id: Long, name: String, avatarIcon: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val normalized = name.trim()
            if (normalized == "未分類" || normalized.length > 80 || normalized.any { it.isISOControl() }) return@launch
            val existing = categoryDao.getCategoryByName(normalized)
            if (existing != null && existing.id != id) return@launch
            categoryDao.updateCategoryMeta(id, normalized, avatarIcon.ifBlank { "life" })
            _editingCategory.value = null
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            val target = categories.value.firstOrNull { it.id == id } ?: return@launch
            // 內建分類不可刪除：僅允許重新命名/換圖
            if (target.isDefault) return@launch
            categoryDao.deleteCategory(id)
            categoryDao.nullOutDanglingCategoryRefs()
            if (_proCategoryFilter.value == id) _proCategoryFilter.value = null
            _editingCategory.value = null
        }
    }

    fun createCategory(name: String, avatarIcon: String) {
        if (name.isBlank() || name.trim() == "未分類" || name.trim().length > 80 || name.any { it.isISOControl() }) return
        val customCount = categories.value.count { !it.isDefault }
        if (!isProUnlocked.value && customCount >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT) {
            _showCreateCategoryDialog.value = false
            _showProLimitNotice.value = true
            return
        }
        viewModelScope.launch {
            categoryDao.getOrCreateCategory(
                CategoryEntity(
                    name = name.trim(),
                    colorArgb = 0xFF5C6BC0.toInt(),
                    avatarIcon = avatarIcon.ifBlank { "life" }
                )
            )
            _showCreateCategoryDialog.value = false
        }
    }

    /**
     * 詳細檢視內建新建分類：建立後直接套用到指定貼文。
     * 與 PRO Tab 的 [createCategory] 共用配額/正規化規則，避免三處 UI 行為分叉；
     * 已存在同名分類則直接套用既有 id（與儲存頁一致）。
     */
    fun createCategoryAndAssign(itemId: Long, name: String, avatarIcon: String) {
        val normalized = name.trim()
        if (normalized.isBlank() || normalized == "未分類" || normalized.length > 80 || normalized.any { it.isISOControl() }) return
        viewModelScope.launch {
            val existing = runCatching { categoryDao.getCategoryByName(normalized) }.getOrNull()
            val customCount = runCatching { categoryDao.getAllCategories().count { !it.isDefault } }
                .getOrDefault(categories.value.count { !it.isDefault })
            if (existing == null && !isProUnlocked.value &&
                customCount >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT) {
                _showProLimitNotice.value = true
                return@launch
            }
            val newId = existing?.id ?: categoryDao.getOrCreateCategory(
                CategoryEntity(
                    name = normalized,
                    colorArgb = 0xFF5C6BC0.toInt(),
                    avatarIcon = avatarIcon.ifBlank { "life" }
                )
            )
            runCatching { repository.updateCategory(itemId, newId) }
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

    fun setKeepDataAfterUninstall(keep: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepDataAfterUninstall(keep) }
    }

    suspend fun getSavedApiKey(): String? {
        return settingsRepository.getOpenAiApiKey()
    }

    private val noteWriteMutex = kotlinx.coroutines.sync.Mutex()

    fun updateManualNote(itemId: Long, note: String) {
        viewModelScope.launch {
            noteWriteMutex.lock()
            try { repository.updateManualNote(itemId, note) }
            finally { noteWriteMutex.unlock() }
        }
    }

    suspend fun summarizeItemWithAi(item: ItemDetail, manualNote: String = item.manualNote): Result<String> {
        val key = settingsRepository.getOpenAiApiKey().orEmpty()
        if (key.isBlank()) return Result.failure(IllegalStateException("請先於設定中配置 API Key"))

        val commentsJoined = item.comments.joinToString("\n") { "${it.author}: ${it.text}" }
        val summaryInput = listOf(item.displayBody, manualNote.takeIf { it.isNotBlank() }?.let {
            "使用者人工筆記：\n$it"
        }).filterNotNull().joinToString("\n\n")
        val res = openAiClient.analyzePost(
            apiKey = key,
            model = selectedModel.value,
            customBaseUrl = customBaseUrl.value,
            postContent = summaryInput,
            commentsContent = commentsJoined
        )

        return res.mapCatching { (analysis, usage) ->
            val summaryText = analysis.summary
            repository.persistAiAnalysis(
                itemId = item.item.id,
                analysis = analysis,
                usage = usage,
                model = selectedModel.value,
                inputHash = openAiClient.computeInputHash(summaryInput + "\n" + commentsJoined),
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
        ActivityResultContracts.CreateDocument("application/octet-stream")
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
            onTriggerUninstallBackup = {
                val defaultName = "reater_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())}.reater"
                reaterExportLauncher.launch(defaultName)
            },
            onOpenIconGallery = { showIconGallery = true },
            onOpenUnlock = { showUnlockDialog = true }
        )
    }

    if (showIconGallery) {
        IconGalleryDialog(
            isPro = isPro,
            currentAvatarId = customAvatarId,
            currentAvatarUri = customAvatarUri,
            onSelectCustomAvatar = { viewModel.setCustomAvatarUri(it) },
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

    // PRO Tab 建分類 Dialog（鍵盤友善版：按鈕不被鍵盤蓋、返回不丟稿、Done 直接建立）
    val showCreateCat by viewModel.showCreateCategoryDialog.collectAsState()
    val createPrefill by viewModel.createCategoryPrefill.collectAsState()
    if (showCreateCat) {
        val customCount = categories.count { !it.isDefault }
        val quotaText = if (!isPro) {
            val remaining = com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT - customCount
            if (remaining > 0) "免費版還可新增 $remaining 個自訂分類（已用 $customCount/3）"
            else "免費版自訂分類已滿（3/3），升級 Pro 可無限新增"
        } else null
        // key 帶 prefill：從下拉「新增 xxx」進來時帶入關鍵字；返回捨棄後草稿不殘留
        androidx.compose.runtime.key(createPrefill) {
            com.reater.app.ui.components.CategoryCreateDialog(
                initialName = createPrefill,
                quotaText = quotaText,
                isPro = isPro,
                onProIconLocked = { showUnlockDialog = true },
                onDismiss = { viewModel.setShowCreateCategoryDialog(false) },
                onConfirm = { name, avatar -> viewModel.createCategory(name, avatar) }
            )
        }
    }

    // 分類編輯 Dialog（入口：PRO 分類頁 → 自訂分類 → ✎；內建分類可改名不可刪除）
    val editingCategory by viewModel.editingCategory.collectAsState()
    editingCategory?.let { editing ->
        // 取最新 entity，避免改名後顯示舊值
        val live = categories.firstOrNull { it.id == editing.id } ?: editing
        com.reater.app.ui.components.CategoryEditDialog(
            category = live,
            isPro = isPro,
            onProIconLocked = { showUnlockDialog = true },
            onDismiss = { viewModel.setEditingCategory(null) },
            onSave = { name, avatar ->
                viewModel.renameCategory(live.id, name, avatar)
                Toast.makeText(context, "分類已更新", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                viewModel.deleteCategory(live.id)
                Toast.makeText(context, "分類已刪除，貼文改為未分類", Toast.LENGTH_SHORT).show()
            }
        )
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
                    // 個人頭像：未解鎖也顯示頭像本體（不再是鎖圖示）。
                    // 未解鎖點擊 → Pro 開通 UI；已解鎖點擊 → 同 Reater 標題的 15 頭像設定頁。
                    IconButton(
                        onClick = {
                            if (isPro) showIconGallery = true
                            else showUnlockDialog = true
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            UserAvatarView(
                                avatarId = customAvatarId,
                                avatarUri = customAvatarUri,
                                size = 32.dp
                            )
                            if (!isPro) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(14.dp)
                                        .background(Color(0xFFFFB300), CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
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
                            selectedFilter = proCategoryFilter,
                            onFilterSelect = viewModel::setProCategoryFilter,
                            active = currentTab == page,
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
                            selectedFilter = proCategoryFilter,
                            onFilterSelect = viewModel::setProCategoryFilter,
                            active = currentTab == page,
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
                            selectedFilter = proCategoryFilter,
                            onFilterSelect = viewModel::setProCategoryFilter,
                            active = currentTab == page,
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
                            onAddCategoryClick = {
                                if (!isPro && categories.count { !it.isDefault } >= com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT)
                                    showUnlockDialog = true
                                else viewModel.setShowCreateCategoryDialog(true)
                            },
                            collections = collections,
                            allItems = allPosts,
                            onOpenUnlock = { showUnlockDialog = true },
                            onEditCategory = { viewModel.setEditingCategory(it) },
                            onMoveCategory = viewModel::moveCategory,
                            onReorderCategories = viewModel::reorderAllCategories,
                            onUpdateRule = viewModel::updateCollectionRule,
                            onSaveCollection = viewModel::updateCollection,
                            onDeleteCollection = viewModel::deleteCollection
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
    onDelete: (ItemDetail) -> Unit,
    selectedFilter: Long? = null,
    onFilterSelect: (Long?) -> Unit = {},
    active: Boolean = true
) {
    val filtered = posts.filter { detail ->
        when (selectedFilter) {
            null -> true
            -1L -> detail.userEdit?.categoryId == null
            else -> detail.userEdit?.categoryId == selectedFilter
        }
    }
    Column(Modifier.fillMaxSize()) {
        com.reater.app.ui.components.CategoryFilterBar(
            categories = categories, selectedId = selectedFilter,
            counts = posts.groupingBy { it.userEdit?.categoryId ?: -1L }.eachCount(),
            totalCount = posts.size, onSelect = onFilterSelect,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), active = active
        )
    if (filtered.isEmpty()) {
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
            items(filtered, key = { it.item.id }) { itemDetail ->
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
}

@Composable
private fun ProCategoryTabContent(
    categories: List<CategoryEntity>,
    isPro: Boolean,
    onAddCategoryClick: () -> Unit,
    collections: List<SavedCollectionEntity>,
    allItems: List<ItemDetail> = emptyList(),
    onOpenUnlock: () -> Unit,
    onEditCategory: (CategoryEntity) -> Unit,
    onMoveCategory: (CategoryEntity, Int) -> Unit,
    onReorderCategories: (List<CategoryEntity>) -> Unit = {},
    onUpdateRule: (SavedCollectionEntity, CollectionRule) -> Unit,
    onSaveCollection: (SavedCollectionEntity, String, String, CollectionRule) -> Unit = { _, _, _, _ -> },
    onDeleteCollection: ((SavedCollectionEntity) -> Unit)? = null
) {
    var editingRuleCollection by remember { mutableStateOf<SavedCollectionEntity?>(null) }
    val json = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    fun ruleFor(collection: SavedCollectionEntity): CollectionRule = runCatching {
        json.decodeFromString(CollectionRule.serializer(), collection.rulesJson).let { it.copy(enabled = collection.isEnabled and it.enabled) }
    }.getOrDefault(CollectionRule(enabled = collection.isEnabled))

    var orderedCategories by remember(categories) { mutableStateOf(categories) }
    var draggingCategoryId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemSpacingPx = remember(density) { with(density) { 10.dp.toPx() } }
    var measuredItemHeightPx by remember(density) { mutableFloatStateOf(with(density) { 56.dp.toPx() }) }
    val slotPitchPx = measuredItemHeightPx + itemSpacingPx

    fun settleDrag() {
        settleJob?.cancel()
        val residual = dragOffsetY
        if (residual == 0f) {
            draggingCategoryId = null
            settleJob = null
            return
        }
        settleJob = coroutineScope.launch {
            animate(initialValue = residual, targetValue = 0f, animationSpec = tween(150)) { value, _ ->
                dragOffsetY = value
            }
            draggingCategoryId = null
            dragOffsetY = 0f
        }
    }

    LaunchedEffect(categories) {
        if (draggingCategoryId == null) {
            orderedCategories = categories
        }
    }

    editingRuleCollection?.let { collection ->
        val original = ruleFor(collection)
        com.reater.app.ui.components.SmartCollectionRuleDialog(
            collection = collection,
            initialRule = original,
            categories = categories,
            allItems = allItems,
            isPro = isPro,
            onDismiss = { editingRuleCollection = null },
            onSave = { newName, newIcon, updatedRule ->
                onSaveCollection(collection, newName, newIcon, updatedRule)
                editingRuleCollection = null
            },
            onDelete = {
                onDeleteCollection?.invoke(collection)
                editingRuleCollection = null
            },
            onResetToDefault = {
                val def = SmartCollectionEngine.getDefaultRule(collection.name, collection.iconName)
                val defName = defaultCollectionDisplayName(collection.name, collection.iconName)
                onSaveCollection(collection, defName, collection.iconName, def)
                editingRuleCollection = null
            }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("分類管理", style = MaterialTheme.typography.titleMedium)
            Text(if (isPro) "共 ${categories.size} 個分類，可自由排序" else "免費 ${categories.count { !it.isDefault }}/3・升級後分類無上限",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAddCategoryClick, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增分類") }
        }
        items(orderedCategories, key = { "category_${it.id}" }) { category ->
            val isDragging = draggingCategoryId == category.id
            val dragScale by animateFloatAsState(
                targetValue = if (isDragging) 1.03f else 1f,
                animationSpec = tween(150),
                label = "categoryDragScale"
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val measured = coordinates.size.height.toFloat()
                        if (measured > 0f && measured != measuredItemHeightPx) {
                            measuredItemHeightPx = measured
                        }
                    }
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragging) 10f else 1f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                        }
                        scaleX = dragScale
                        scaleY = dragScale
                    },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
                border = if (isDragging) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.reater.app.ui.components.CategoryDragHandle(
                        id = category.id,
                        onDragStart = {
                            settleJob?.cancel()
                            settleJob = null
                            draggingCategoryId = category.id
                            dragOffsetY = 0f
                        },
                        onDrag = { deltaY ->
                            dragOffsetY += deltaY
                            var currentIndex = orderedCategories.indexOfFirst { it.id == category.id }
                            while (currentIndex != -1 && dragOffsetY > slotPitchPx / 2f && currentIndex < orderedCategories.lastIndex) {
                                val mutable = orderedCategories.toMutableList()
                                java.util.Collections.swap(mutable, currentIndex, currentIndex + 1)
                                orderedCategories = mutable
                                dragOffsetY -= slotPitchPx
                                currentIndex += 1
                            }
                            while (currentIndex != -1 && dragOffsetY < -slotPitchPx / 2f && currentIndex > 0) {
                                val mutable = orderedCategories.toMutableList()
                                java.util.Collections.swap(mutable, currentIndex, currentIndex - 1)
                                orderedCategories = mutable
                                dragOffsetY += slotPitchPx
                                currentIndex -= 1
                            }
                            if (currentIndex == 0 && dragOffsetY < 0f) dragOffsetY = 0f
                            if (currentIndex == orderedCategories.lastIndex && dragOffsetY > 0f) dragOffsetY = 0f
                        },
                        onDragEnd = {
                            if (orderedCategories != categories) {
                                onReorderCategories(orderedCategories)
                            }
                            settleDrag()
                        },
                        onDragCancel = {
                            if (orderedCategories != categories) {
                                onReorderCategories(orderedCategories)
                            }
                            settleDrag()
                        },
                        onAccessibilityMove = { direction ->
                            val currentIndex = orderedCategories.indexOfFirst { it.id == category.id }
                            val target = currentIndex + direction
                            if (currentIndex != -1 && target in orderedCategories.indices) {
                                val mutable = orderedCategories.toMutableList()
                                java.util.Collections.swap(mutable, currentIndex, target)
                                orderedCategories = mutable
                                onReorderCategories(mutable)
                            }
                        }
                    )
                    com.reater.app.ui.components.CategoryIcon(category)
                    Spacer(Modifier.width(8.dp))
                    Text(category.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { onEditCategory(category) }) { Icon(Icons.Default.Edit, "編輯分類") }
                }
            }
        }
        item { Text("智慧分類規則", style = MaterialTheme.typography.titleMedium) }
        if (!isPro) {
            item {
                Text("PRO 可依作者、標籤、讚數、閱讀與收藏狀態自動篩選，並隨時停用規則。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProUnlockCard(onUnlockClick = onOpenUnlock)
            }
        } else {
            items(collections, key = { "rule_${it.id}" }) { collection ->
                val rule = ruleFor(collection)
                val isRuleEnabled = rule.enabled
                val hasConditions = rule.hasActiveConditions()
                val summaryText = SmartCollectionEngine.generateRuleSummary(rule, categories)
                val matchedCount = remember(rule, allItems, categories) {
                    if (!hasConditions) 0
                    else allItems.count { SmartCollectionEngine.matchesItem(rule, it, categories) }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingRuleCollection = collection },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = if (isRuleEnabled) 0.45f else 0.22f
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 既有/專屬 SVG 向量圖示容器（禁止使用 Emoji）
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isRuleEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(AvatarIcons.getDrawableRes(collection.iconName)),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isRuleEnabled) Color.Unspecified else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // 資訊層級：智慧分類名稱 -> 規則摘要 -> 符合篇數
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = com.reater.app.domain.defaultCollectionDisplayName(collection.name, collection.iconName),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isRuleEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )

                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRuleEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = if (!isRuleEnabled) "已停用"
                                else if (hasConditions) "符合 $matchedCount 篇"
                                else "尚未設定條件",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRuleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                fontWeight = if (isRuleEnabled) FontWeight.Medium else FontWeight.Normal
                            )
                        }

                        // Toggle：只負責啟用 / 停用規則
                        Switch(
                            checked = isRuleEnabled,
                            onCheckedChange = { isChecked ->
                                onUpdateRule(collection, rule.copy(enabled = isChecked))
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
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

