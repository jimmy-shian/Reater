package com.reater.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.reater.app.domain.CustomKeywordRule
import com.reater.app.ui.components.EditCustomKeywordRuleDialog
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.foundation.layout.statusBarsPadding
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
        val openId = intent?.getLongExtra(com.reater.app.notify.NotifyCenter.EXTRA_OPEN_ITEM_ID, -1L) ?: -1L
        if (openId > 0) {
            pendingOpenItemIdFlow.value = openId
        }
        setContent {
            MainThemedScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 熱啟動（App 已在前景）：同樣丟進 Flow，Compose 端會接著處理
        parseProUnlock(intent.data)?.let { proDeepLinkFlow.value = it }
        val openId = intent.getLongExtra(com.reater.app.notify.NotifyCenter.EXTRA_OPEN_ITEM_ID, -1L)
        if (openId > 0) {
            pendingOpenItemIdFlow.value = openId
        }
    }

    data class ProUnlockDeepLink(val email: String, val code: String)

    companion object {
        /** 待處理的一鍵開通連結（冷/熱啟動共用，Compose 消費後清 null）。 */
        val proDeepLinkFlow = MutableStateFlow<ProUnlockDeepLink?>(null)
        /** 待跳轉並開啟詳細檢視的文章 ID（點擊通知時傳入）。 */
        val pendingOpenItemIdFlow = MutableStateFlow<Long?>(null)

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
                    .let {
                        it.isAppearanceLightStatusBars = darkIcons
                        it.isAppearanceLightNavigationBars = darkIcons
                    }
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = if (isDark) android.graphics.Color.parseColor("#131711")
                    else android.graphics.Color.parseColor("#F6F3EE")
                }
            }
        }
        var showSplashAd by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            MainScreen(viewModel = viewModel)
            if (showSplashAd) {
                SplashAdOverlay(onDismiss = { showSplashAd = false })
            }
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, "https://api.openai.com/v1")

    val selectedModel: StateFlow<String> = settingsRepository.selectedModel
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val aiTransmissionConsent: StateFlow<Boolean> = settingsRepository.aiTransmissionConsent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepDataAfterUninstall: StateFlow<Boolean> = settingsRepository.keepDataAfterUninstall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val smartRuleContentKeywordsEnabled: StateFlow<Boolean> = settingsRepository.smartRuleContentKeywordsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val smartRuleAuthorEnabled: StateFlow<Boolean> = settingsRepository.smartRuleAuthorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val customRulesJsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    val smartRuleCustomKeywords: StateFlow<List<CustomKeywordRule>> = settingsRepository.smartRuleCustomKeywordsJson
        .map { json: String ->
            runCatching {
                customRulesJsonSerializer.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(CustomKeywordRule.serializer()),
                    json
                )
            }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSmartRuleContentKeywordsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSmartRuleContentKeywordsEnabled(enabled) }
    }

    fun setSmartRuleAuthorEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSmartRuleAuthorEnabled(enabled) }
    }

    fun saveCustomKeywordRule(rule: CustomKeywordRule) {
        viewModelScope.launch {
            val current = smartRuleCustomKeywords.value.toMutableList()
            val idx = current.indexOfFirst { it.id == rule.id }
            if (idx >= 0) {
                current[idx] = rule
            } else {
                current.add(rule)
            }
            settingsRepository.setSmartRuleCustomKeywordsJson(customRulesJsonSerializer.encodeToString(kotlinx.serialization.builtins.ListSerializer(CustomKeywordRule.serializer()), current))
        }
    }

    fun deleteCustomKeywordRule(ruleId: String) {
        viewModelScope.launch {
            val current = smartRuleCustomKeywords.value.filter { it.id != ruleId }
            settingsRepository.setSmartRuleCustomKeywordsJson(customRulesJsonSerializer.encodeToString(kotlinx.serialization.builtins.ListSerializer(CustomKeywordRule.serializer()), current))
        }
    }

    fun applySmartClassification(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.applySmartClassificationToAll(onlyUnclassified = false)
            onDone(count)
        }
    }

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
                    colorArgb = 0xFF8B5D34.toInt(),
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
                    colorArgb = 0xFF8B5D34.toInt(),
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

    suspend fun exportJson(
        output: java.io.OutputStream,
        onProgress: (com.reater.app.data.backup.BackupProgress) -> Unit = {}
    ) = backupManager.exportToJson(output, onProgress)

    suspend fun exportCsv(output: java.io.OutputStream) = backupManager.exportToCsv(output)

    suspend fun importJson(
        input: java.io.InputStream,
        onProgress: (com.reater.app.data.backup.BackupProgress) -> Unit = {}
    ) = backupManager.importFromJson(input, onProgress)

    fun saveAiConfig(apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            if (apiKey.isNotBlank()) settingsRepository.setOpenAiApiKey(apiKey)
            if (baseUrl.isNotBlank()) settingsRepository.setCustomBaseUrl(baseUrl)
            if (model.isNotBlank()) settingsRepository.setSelectedModel(model)
        }
    }

    suspend fun fetchAvailableModels(apiKey: String, baseUrl: String): Result<List<String>> =
        openAiClient.fetchModels(apiKey, baseUrl)

    fun setAiTransmissionConsent(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiTransmissionConsent(enabled) }
    }

    fun setKeepDataAfterUninstall(keep: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepDataAfterUninstall(keep) }
    }

    suspend fun getSavedApiKey(): String? {
        return settingsRepository.getOpenAiApiKey()
    }

    val aiAnalyzeOnSave: StateFlow<Boolean> = settingsRepository.aiAnalyzeOnSave
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAiAnalyzeOnSave(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiAnalyzeOnSave(enabled) }
    }

    suspend fun getItemDetailById(id: Long): ItemDetail? = repository.getItemDetailById(id)
    fun observePostDetail(id: Long): kotlinx.coroutines.flow.Flow<ItemDetail?> = repository.observePostDetail(id)

    private val noteWriteMutex = kotlinx.coroutines.sync.Mutex()

    fun updateManualNote(itemId: Long, note: String) {
        viewModelScope.launch {
            noteWriteMutex.lock()
            try { repository.updateManualNote(itemId, note) }
            finally { noteWriteMutex.unlock() }
        }
    }

    private val _aiActiveItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val aiActiveItemIds: StateFlow<Set<Long>> = _aiActiveItemIds

    fun startBackgroundAiAnalysis(context: Context, item: ItemDetail, manualNote: String = item.manualNote) {
        val itemId = item.item.id
        _aiActiveItemIds.update { it + itemId }
        com.reater.app.notify.AiAnalysisWorker.schedule(
            context = context.applicationContext,
            itemId = itemId,
            postTitle = item.displayBody.take(40),
            manualNote = manualNote
        )
        // 觀察工作是否已完成（透過 Flow 同步更新狀態）
        viewModelScope.launch {
            val workManager = androidx.work.WorkManager.getInstance(context.applicationContext)
            workManager.getWorkInfosForUniqueWorkFlow("ai_analysis_work_$itemId").collect { workInfoList ->
                val state = workInfoList.firstOrNull()?.state
                if (state != null && state.isFinished) {
                    _aiActiveItemIds.update { it - itemId }
                }
            }
        }
    }

    suspend fun summarizeItemWithAi(item: ItemDetail, manualNote: String = item.manualNote): Result<String> {
        if (!aiTransmissionConsent.value) {
            return Result.failure(IllegalStateException(appContext.getString(com.reater.app.R.string.main_error_ai_not_authorized)))
        }
        val key = settingsRepository.getOpenAiApiKey().orEmpty()
        if (key.isBlank()) return Result.failure(IllegalStateException(appContext.getString(com.reater.app.R.string.main_error_no_api_key)))

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
    val contentKeywordsRuleEnabled by viewModel.smartRuleContentKeywordsEnabled.collectAsState()
    val authorRuleEnabled by viewModel.smartRuleAuthorEnabled.collectAsState()
    val customKeywordRules by viewModel.smartRuleCustomKeywords.collectAsState()
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
    // 收藏、未讀、全部：三者的平台篩選與分類篩選各自獨立，數量與選取狀態互不混雜
    var allPlatformFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("ALL") }
    var unreadPlatformFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("ALL") }
    var favoritePlatformFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("ALL") }

    var allCategoryFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
    var unreadCategoryFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
    var favoriteCategoryFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }

    // 當從外部（如 PRO 分類點擊 openCategory）切換過來時，同步至「全部」分類篩選
    LaunchedEffect(proCategoryFilter) {
        if (proCategoryFilter != null) {
            allCategoryFilter = proCategoryFilter
        }
    }

    // 分類被刪除時，自動清除各分頁無效的篩選
    LaunchedEffect(categories) {
        val validIds = categories.map { it.id }.toSet()
        if (allCategoryFilter != null && allCategoryFilter != -1L && allCategoryFilter !in validIds) allCategoryFilter = null
        if (unreadCategoryFilter != null && unreadCategoryFilter != -1L && unreadCategoryFilter !in validIds) unreadCategoryFilter = null
        if (favoriteCategoryFilter != null && favoriteCategoryFilter != -1L && favoriteCategoryFilter !in validIds) favoriteCategoryFilter = null
    }

    // 與畫面各分頁獨立篩選一致的雙重篩選（平台 + 分類），供上方分享鍵複製當前畫面上看到的清單
    fun applyTabFilters(base: List<ItemDetail>, platform: String, categoryId: Long?): List<ItemDetail> {
        return base.filter { detail ->
            platform == "ALL" || detail.item.platform == platform
        }.filter { detail ->
            when (categoryId) {
                null -> true
                -1L -> detail.userEdit?.categoryId == null
                else -> detail.userEdit?.categoryId == categoryId
            }
        }
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 通知開關與系統權限確認：App 內預設開啟；若 App 內開啟但系統未授權，每次打開 App 主動詢問；若 App 內關閉則不詢問。
    val reviewDigestEnabled by viewModel.reviewDigestEnabled.collectAsState()
    val unreadNudgeEnabled by viewModel.unreadNudgeEnabled.collectAsState()
    val appNotifyWanted = reviewDigestEnabled || unreadNudgeEnabled

    var showNotifPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var hasPromptedNotifThisSession by rememberSaveable { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            val canNotifyNow = com.reater.app.notify.NotifyCenter.canNotify(context)
            if (!canNotifyNow) {
                showNotifPermissionDialog = true
            }
        }
    }

    val checkAndPromptNotificationPermission = remember(appNotifyWanted, context) {
        {
            if (appNotifyWanted && !hasPromptedNotifThisSession) {
                val systemGranted = com.reater.app.notify.NotifyCenter.canNotify(context)
                if (!systemGranted) {
                    hasPromptedNotifThisSession = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val activity = context as? Activity
                        val shouldShowRationale = activity?.let {
                            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                        } ?: false
                        if (shouldShowRationale) {
                            showNotifPermissionDialog = true
                        } else {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        showNotifPermissionDialog = true
                    }
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, checkAndPromptNotificationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAndPromptNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val ioScope = androidx.compose.runtime.rememberCoroutineScope()

    val customAvatarId by viewModel.customAvatarId.collectAsState()
    val customAvatarUri by viewModel.customAvatarUri.collectAsState()
    var showIconGallery by remember { mutableStateOf(false) }

    var backupProgress by remember { mutableStateOf<com.reater.app.data.backup.BackupProgress?>(null) }

    val reaterExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) ioScope.launch {
            backupProgress = com.reater.app.data.backup.BackupProgress(0, 1, context.getString(R.string.main_export_preparing))
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        viewModel.exportJson(out) { prog ->
                            backupProgress = prog
                        }
                    } ?: error(context.getString(R.string.main_error_open_export_file))
                }
            }
            backupProgress = null
            Toast.makeText(
                context,
                if (result.isSuccess) context.getString(R.string.main_export_done)
                else context.getString(R.string.main_export_failed, result.exceptionOrNull()?.localizedMessage ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val reaterImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ioScope.launch {
            backupProgress = com.reater.app.data.backup.BackupProgress(0, 1, context.getString(R.string.main_import_reading))
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
                        error(context.getString(R.string.main_error_invalid_file_type))
                    }
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        viewModel.importJson(inp) { prog ->
                            backupProgress = prog
                        }.getOrThrow()
                    } ?: error(context.getString(R.string.main_error_open_backup_file))
                }
            }
            backupProgress = null
            Toast.makeText(
                context,
                result.fold({ context.getString(R.string.main_restore_done, it) }, { context.getString(R.string.main_restore_failed, it.localizedMessage ?: "") }),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    backupProgress?.let { prog ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prog.stage,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(prog.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { prog.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (prog.detail.isNotBlank()) {
                            Text(
                                text = prog.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        prog.etaText(LocalContext.current)?.let { etaText ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = etaText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
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

    // 通知點擊帶入 ID：自動尋找該文章並展開詳細檢視（避免 LaunchedEffect 監聽 key 變更時自消取消協程）
    LaunchedEffect(Unit) {
        MainActivity.pendingOpenItemIdFlow.collect { id ->
            if (id != null && id > 0) {
                val target = viewModel.getItemDetailById(id)
                if (target != null) {
                    showSettingsDialog = false
                    showUnlockDialog = false
                    selectedItemForDetail = target
                    viewModel.markAsRead(target)
                    viewModel.recordOpen(target)
                }
                MainActivity.pendingOpenItemIdFlow.value = null
            }
        }
    }

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
            if (success) context.getString(R.string.main_pro_unlock_success)
            else context.getString(R.string.main_pro_deeplink_failed),
            Toast.LENGTH_LONG
        ).show()
        if (!success) showUnlockDialog = true
    }

    if (showNotifPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotifPermissionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.main_notif_permission_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.main_notif_permission_dialog_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotifPermissionDialog = false
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(fallback)
                            } catch (_: Exception) {}
                        }
                    }
                ) {
                    Text(stringResource(R.string.main_button_go_settings), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifPermissionDialog = false }) {
                    Text(stringResource(R.string.main_button_later))
                }
            }
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.main_trash_move_dialog_title)) },
            text = { Text(stringResource(R.string.main_trash_move_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete?.let { viewModel.moveToTrash(it) }
                        itemToDelete = null
                        Toast.makeText(context, context.getString(R.string.main_trash_moved_with_retention), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.detail_action_move_to_trash))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text(stringResource(R.string.main_trash_clear_title)) },
            text = { Text(stringResource(R.string.main_trash_clear_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyTrashConfirm = false
                        Toast.makeText(context, context.getString(R.string.main_trash_cleared), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.main_trash_clear_button))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (selectedItemForDetail != null) {
        // 以最新 Flow 資料解析，改分類 / AI 摘要後徽章與內容即時更新
        val liveDetail = posts.firstOrNull { it.item.id == selectedItemForDetail!!.item.id }
            ?.takeIf { it.aiSummary.isNotBlank() || selectedItemForDetail!!.aiSummary.isBlank() }
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
                Toast.makeText(context, context.getString(R.string.main_moved_to_trash), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.main_avatar_changed), Toast.LENGTH_SHORT).show()
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
            if (remaining > 0) stringResource(
                R.string.free_category_limit_remaining, remaining, customCount,
                com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT
            )
            else stringResource(
                R.string.free_category_limit_full,
                com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT,
                com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT
            )
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
                Toast.makeText(context, context.getString(R.string.detail_toast_category_updated), Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                viewModel.deleteCategory(live.id)
                Toast.makeText(context, context.getString(R.string.main_category_deleted), Toast.LENGTH_SHORT).show()
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
                        title = stringResource(R.string.pro_share_limit_title),
                        onClose = { viewModel.setShowProLimitNotice(false) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.pro_share_limit_body, com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT),
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
                            Text(stringResource(R.string.ok))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            viewModel.setShowProLimitNotice(false)
                            showUnlockDialog = true
                        }) {
                            Text(stringResource(R.string.pro_lock_cta))
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showIconGallery = true }
                            .padding(vertical = 4.dp)
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

                    IconButton(
                        onClick = {
                            val activePosts = when (currentTab) {
                                3 -> proFilteredPosts
                                0 -> applyTabFilters(if (searchQuery.isNotBlank()) posts else allPosts, allPlatformFilter, allCategoryFilter)
                                1 -> applyTabFilters(if (searchQuery.isNotBlank()) posts.filter { !it.isRead } else unreadPosts, unreadPlatformFilter, unreadCategoryFilter)
                                2 -> applyTabFilters(if (searchQuery.isNotBlank()) posts.filter { it.isFavorite } else favoritePosts, favoritePlatformFilter, favoriteCategoryFilter)
                                else -> emptyList()
                            }
                            if (activePosts.isNotEmpty()) {
                                val shareText = activePosts.joinToString("\n") { detail ->
                                    formatPostShareText(detail)
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Reater Group Share", shareText))
                                Toast.makeText(context, context.getString(R.string.main_copied_filter_with_count, activePosts.size), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.main_no_posts_in_filter), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.main_accessibility_copy_filter),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isPro) showIconGallery = true
                            else showUnlockDialog = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            UserAvatarView(
                                avatarId = customAvatarId,
                                avatarUri = customAvatarUri,
                                size = 30.dp
                            )
                            if (!isPro) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(13.dp)
                                        .background(Color(0xFFFFB300), CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissFocusOnTap(focusManager)
        ) {
            // 緊湊搜尋列：佔位小（38dp，垂直外距 4dp），為下方列表保留最大閱讀空間
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                    .height(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.onSearchQueryChanged("")
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.main_accessibility_clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

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
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    modifier = Modifier.height(38.dp),
                    selected = pagerState.currentPage == 0,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.tab_all)) }
                )
                val isUnreadSelected = pagerState.currentPage == 1
                val unreadCount = unreadPosts.size
                val unreadBadgeText = when {
                    unreadCount <= 0 -> null
                    unreadCount > 99 -> "99+"
                    else -> unreadCount.toString()
                }
                Tab(
                    modifier = Modifier.height(38.dp),
                    selected = isUnreadSelected,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(1) } },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.tab_unread),
                                fontWeight = if (isUnreadSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (unreadBadgeText != null) {
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 22.dp)
                                        .height(20.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .background(
                                            if (isUnreadSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = unreadBadgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 11.sp
                                        ),
                                        color = if (isUnreadSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    modifier = Modifier.height(38.dp),
                    selected = pagerState.currentPage == 2,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.tab_favorite)) }
                )
                Tab(
                    modifier = Modifier.height(38.dp),
                    selected = pagerState.currentPage == 3,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(3) } },
                    text = { Text(stringResource(R.string.tab_pro_categories)) }
                )
                Tab(
                    modifier = Modifier.height(38.dp),
                    selected = pagerState.currentPage == 4,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(4) } },
                    text = { Text(stringResource(R.string.tab_trash)) }
                )
                Tab(
                    modifier = Modifier.height(38.dp),
                    selected = pagerState.currentPage == 5,
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage(5) } },
                    text = { Text(stringResource(R.string.tab_analytics)) }
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
                            selectedFilter = allCategoryFilter,
                            onFilterSelect = {
                                allCategoryFilter = it
                                viewModel.setProCategoryFilter(it)
                            },
                            selectedPlatform = allPlatformFilter,
                            onPlatformSelect = { allPlatformFilter = it },
                            active = currentTab == page,
                            emptyMessage = if (searchQuery.isNotBlank()) stringResource(R.string.main_search_no_match, searchQuery)
                            else stringResource(R.string.main_empty_all_hint),
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
                                    if (willBeRead) context.getString(R.string.main_marked_read) else context.getString(R.string.main_marked_unread),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = { detail ->
                                val willFav = !detail.isFavorite
                                viewModel.toggleFavorite(detail)
                                Toast.makeText(
                                    context,
                                    if (willFav) context.getString(R.string.main_favorited) else context.getString(R.string.main_unfavorited),
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
                            selectedFilter = unreadCategoryFilter,
                            onFilterSelect = { unreadCategoryFilter = it },
                            selectedPlatform = unreadPlatformFilter,
                            onPlatformSelect = { unreadPlatformFilter = it },
                            active = currentTab == page,
                            emptyMessage = if (searchQuery.isNotBlank()) stringResource(R.string.main_search_no_match_unread, searchQuery)
                            else stringResource(R.string.main_all_read_congrats),
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
                                    if (willBeRead) context.getString(R.string.main_marked_read) else context.getString(R.string.main_marked_unread),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = { detail ->
                                val willFav = !detail.isFavorite
                                viewModel.toggleFavorite(detail)
                                Toast.makeText(
                                    context,
                                    if (willFav) context.getString(R.string.main_favorited) else context.getString(R.string.main_unfavorited),
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
                            selectedFilter = favoriteCategoryFilter,
                            onFilterSelect = { favoriteCategoryFilter = it },
                            selectedPlatform = favoritePlatformFilter,
                            onPlatformSelect = { favoritePlatformFilter = it },
                            active = currentTab == page,
                            emptyMessage = if (searchQuery.isNotBlank()) stringResource(R.string.main_search_no_match_favorite, searchQuery)
                            else stringResource(R.string.main_empty_favorite_hint),
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
                                    if (willBeRead) context.getString(R.string.main_marked_read) else context.getString(R.string.main_marked_unread),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onToggleFavorite = { detail ->
                                val willFav = !detail.isFavorite
                                viewModel.toggleFavorite(detail)
                                Toast.makeText(
                                    context,
                                    if (willFav) context.getString(R.string.main_favorited) else context.getString(R.string.main_unfavorited),
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
                            onDeleteCollection = viewModel::deleteCollection,
                            contentKeywordsRuleEnabled = contentKeywordsRuleEnabled,
                            onToggleContentKeywordsRule = viewModel::setSmartRuleContentKeywordsEnabled,
                            authorRuleEnabled = authorRuleEnabled,
                            onToggleAuthorRule = viewModel::setSmartRuleAuthorEnabled,
                            customKeywordRules = customKeywordRules,
                            onSaveCustomKeywordRule = viewModel::saveCustomKeywordRule,
                            onDeleteCustomKeywordRule = viewModel::deleteCustomKeywordRule,
                            onApplySmartClassification = {
                                viewModel.applySmartClassification { count ->
                                    Toast.makeText(context, context.getString(R.string.main_smart_rule_applied, count), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    4 -> {
                        TrashTabContent(
                            trashPosts = trashPosts,
                            onEmptyTrash = { showEmptyTrashConfirm = true },
                            onRestore = { detail ->
                                viewModel.restoreFromTrash(detail)
                                Toast.makeText(context, context.getString(R.string.main_restore_success), Toast.LENGTH_SHORT).show()
                            },
                            onDeletePermanently = { detail ->
                                viewModel.deletePermanently(detail)
                                Toast.makeText(context, context.getString(R.string.main_deleted_permanently), Toast.LENGTH_SHORT).show()
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
    /** 平台篩選："ALL" | UrlParser.PLATFORM_THREADS | PLATFORM_INSTAGRAM | PLATFORM_X */
    selectedPlatform: String = "ALL",
    onPlatformSelect: (String) -> Unit = {},
    platformCounts: Map<String, Int>? = null,
    active: Boolean = true
) {
    // 依據本分頁文章清單獨立計算各平台數量（完全獨立，不與其他分頁混雜）
    val tabPlatformCounts = remember(posts, platformCounts) {
        platformCounts ?: mapOf(
            "ALL" to posts.size,
            com.reater.app.domain.UrlParser.PLATFORM_THREADS to posts.count { it.item.platform == com.reater.app.domain.UrlParser.PLATFORM_THREADS },
            com.reater.app.domain.UrlParser.PLATFORM_INSTAGRAM to posts.count { it.item.platform == com.reater.app.domain.UrlParser.PLATFORM_INSTAGRAM },
            com.reater.app.domain.UrlParser.PLATFORM_X to posts.count { it.item.platform == com.reater.app.domain.UrlParser.PLATFORM_X },
            com.reater.app.domain.UrlParser.PLATFORM_FACEBOOK to posts.count { it.item.platform == com.reater.app.domain.UrlParser.PLATFORM_FACEBOOK },
            com.reater.app.domain.UrlParser.PLATFORM_YOUTUBE to posts.count { it.item.platform == com.reater.app.domain.UrlParser.PLATFORM_YOUTUBE }
        )
    }

    // 當此分頁中選取的平台數量歸零時，該分頁自動退回 "ALL"，不影響其他分頁
    LaunchedEffect(tabPlatformCounts, selectedPlatform) {
        if (selectedPlatform != "ALL" && (tabPlatformCounts[selectedPlatform] ?: 0) == 0) {
            onPlatformSelect("ALL")
        }
    }

    // 雙重篩選第一層：平台（全部 / Threads / IG / X）
    val platformFiltered = if (selectedPlatform == "ALL") posts
    else posts.filter { it.item.platform == selectedPlatform }
    val filtered = platformFiltered.filter { detail ->
        when (selectedFilter) {
            null -> true
            -1L -> detail.userEdit?.categoryId == null
            else -> detail.userEdit?.categoryId == selectedFilter
        }
    }
    Column(Modifier.fillMaxSize()) {
        com.reater.app.ui.components.CategoryFilterBar(
            categories = categories, selectedId = selectedFilter,
            counts = platformFiltered.groupingBy { it.userEdit?.categoryId ?: -1L }.eachCount(),
            totalCount = platformFiltered.size, onSelect = onFilterSelect,
            selectedPlatform = selectedPlatform,
            onPlatformSelect = onPlatformSelect,
            platformCounts = tabPlatformCounts,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), active = active
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProCategoryTabContent(
    categories: List<CategoryEntity>,
    isPro: Boolean,
    onAddCategoryClick: () -> Unit,
    collections: List<SavedCollectionEntity> = emptyList(),
    allItems: List<ItemDetail> = emptyList(),
    onOpenUnlock: () -> Unit,
    onEditCategory: (CategoryEntity) -> Unit,
    onMoveCategory: (CategoryEntity, Int) -> Unit,
    onReorderCategories: (List<CategoryEntity>) -> Unit = {},
    onUpdateRule: (SavedCollectionEntity, CollectionRule) -> Unit = { _, _ -> },
    onSaveCollection: (SavedCollectionEntity, String, String, CollectionRule) -> Unit = { _, _, _, _ -> },
    onDeleteCollection: ((SavedCollectionEntity) -> Unit)? = null,
    contentKeywordsRuleEnabled: Boolean = true,
    onToggleContentKeywordsRule: (Boolean) -> Unit = {},
    authorRuleEnabled: Boolean = true,
    onToggleAuthorRule: (Boolean) -> Unit = {},
    customKeywordRules: List<CustomKeywordRule> = emptyList(),
    onSaveCustomKeywordRule: (CustomKeywordRule) -> Unit = {},
    onDeleteCustomKeywordRule: (String) -> Unit = {},
    onApplySmartClassification: () -> Unit = {}
) {
    var editingCustomRule by remember { mutableStateOf<CustomKeywordRule?>(null) }
    var showAddCustomRuleDialog by remember { mutableStateOf(false) }

    var orderedCategories by remember(categories) { mutableStateOf(categories) }
    var draggingCategoryId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val itemSpacingPx = remember(density) { with(density) { 10.dp.toPx() } }
    var measuredItemHeightPx by remember(density) { mutableFloatStateOf(with(density) { 42.dp.toPx() }) }
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

    if (showAddCustomRuleDialog) {
        EditCustomKeywordRuleDialog(
            initialRule = null,
            categories = categories,
            onDismiss = { showAddCustomRuleDialog = false },
            onSave = { newRule ->
                onSaveCustomKeywordRule(newRule)
                showAddCustomRuleDialog = false
            }
        )
    }

    editingCustomRule?.let { rule ->
        EditCustomKeywordRuleDialog(
            initialRule = rule,
            categories = categories,
            onDismiss = { editingCustomRule = null },
            onSave = { updatedRule ->
                onSaveCustomKeywordRule(updatedRule)
                editingCustomRule = null
            },
            onDelete = { ruleId ->
                onDeleteCustomKeywordRule(ruleId)
                editingCustomRule = null
            }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.main_category_management), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (isPro) stringResource(R.string.main_category_count_summary, categories.size)
            else stringResource(R.string.main_category_free_limit_summary, categories.count { !it.isDefault }, com.reater.app.domain.OnDeviceClassifier.FREE_CUSTOM_CATEGORY_LIMIT),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onAddCategoryClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.main_category_add_new)) }
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
                    .height(42.dp)
                    .zIndex(if (isDragging) 10f else 1f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                        }
                        scaleX = dragScale
                        scaleY = dragScale
                    },
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
                border = if (isDragging) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.reater.app.ui.components.CategoryDragHandle(
                        id = category.id,
                        modifier = Modifier.size(32.dp),
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
                    Spacer(Modifier.width(4.dp))
                    com.reater.app.ui.components.CategoryIcon(category, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { onEditCategory(category) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, stringResource(R.string.main_category_edit), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.main_smart_rules_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.main_smart_rules_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isPro) {
            item {
                ProUnlockCard(onUnlockClick = onOpenUnlock)
            }
        }

        // 規則 1：自動根據內文關鍵字去分類（開關）
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (contentKeywordsRuleEnabled) 0.45f else 0.22f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_rule_filter),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.main_smart_rule_keyword_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.main_smart_rule_keyword_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = contentKeywordsRuleEnabled,
                        onCheckedChange = { if (isPro) onToggleContentKeywordsRule(it) else onOpenUnlock() }
                    )
                }
            }
        }

        // 規則 2：根據作者名稱去分類（開關）
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (authorRuleEnabled) 0.45f else 0.22f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_rule_sparkles),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.main_smart_rule_author_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.main_smart_rule_author_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = authorRuleEnabled,
                        onCheckedChange = { if (isPro) onToggleAuthorRule(it) else onOpenUnlock() }
                    )
                }
            }
        }

        // 規則 3：根據特定關鍵字分類到指定標籤/分類（客製化輸入比對）
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.main_smart_rule_custom_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.main_smart_rule_custom_priority),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.main_smart_rule_custom_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 規則清單
                    if (customKeywordRules.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.main_smart_rule_custom_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customKeywordRules.forEach { rule ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isPro) editingCustomRule = rule
                                            else onOpenUnlock()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1:1 左側：比對文字（單行超過用"..."）
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ) {
                                            Text(
                                                text = "「${rule.keyword}」",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Text(
                                            text = "➔",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )

                                        // 1:1 右側：目標分類（使用原本的分類圖示，單行超過用"..."）
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                val cat = categories.firstOrNull { it.id == rule.targetCategoryId }
                                                if (cat != null) {
                                                    com.reater.app.ui.components.CategoryIcon(cat, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = cat.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                } else {
                                                    Text(
                                                        text = rule.targetCategoryName.ifBlank { stringResource(R.string.main_smart_rule_target_category_fallback) },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                }
                                                if (rule.targetTagName.isNotBlank()) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "(${rule.targetTagName})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                if (isPro) editingCustomRule = rule
                                                else onOpenUnlock()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.icon_gallery_edit), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (isPro) onDeleteCustomKeywordRule(rule.id)
                                                else onOpenUnlock()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = stringResource(R.string.delete),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ＋ 新增特定關鍵字比對按鈕
                    OutlinedButton(
                        onClick = {
                            if (isPro) showAddCustomRuleDialog = true
                            else onOpenUnlock()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.main_smart_rule_add_rule))
                    }
                }
            }
        }

        // 重新套用智慧分類按鈕
        item {
            Button(
                onClick = onApplySmartClassification,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.main_smart_rule_reapply_hint))
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
                    text = stringResource(R.string.main_trash_empty_hint),
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
                        text = stringResource(R.string.main_trash_count_title, trashPosts.size),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    OutlinedButton(
                        onClick = onEmptyTrash,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.main_trash_clear_button), fontSize = 12.sp)
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
                        text = stringResource(R.string.main_trash_remaining_days, remainingDays),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val emptyBodyText = stringResource(R.string.post_card_empty_body)
            Text(
                text = remember(itemDetail.displayBody) {
                    itemDetail.displayBody.trim()
                        .replace(Regex("\n{3,}"), "\n\n")
                        .ifBlank { emptyBodyText }
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
                    Text(stringResource(R.string.main_trash_restore_button), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDeletePermanently,
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.main_delete_permanently), fontSize = 12.sp)
                }
            }
        }
    }
}