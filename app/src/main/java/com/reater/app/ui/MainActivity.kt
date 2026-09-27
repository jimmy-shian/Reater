package com.reater.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reater.app.R
import com.reater.app.data.local.dao.ProDao
import com.reater.app.data.local.entity.ItemDetail
import com.reater.app.data.local.entity.SavedCollectionEntity
import com.reater.app.data.remote.OpenAiClient
import com.reater.app.data.repository.SettingsRepository
import com.reater.app.data.repository.ThreadPostRepository
import com.reater.app.domain.SmartCollectionEngine
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ThreadPostRepository,
    private val settingsRepository: SettingsRepository,
    private val smartCollectionEngine: SmartCollectionEngine,
    private val proDao: ProDao,
    private val openAiClient: OpenAiClient
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _tabIndex = MutableStateFlow(0) // 0: All, 1: Unread, 2: Favorite, 3: Pro Smart Collections
    val tabIndex: StateFlow<Int> = _tabIndex

    val isProUnlocked: StateFlow<Boolean> = settingsRepository.isProUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val customBaseUrl: StateFlow<String> = settingsRepository.customBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1")

    val selectedModel: StateFlow<String> = settingsRepository.selectedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gpt-5-nano")

    val smartCollections: StateFlow<List<SavedCollectionEntity>> = proDao.observeAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val posts: StateFlow<List<ItemDetail>> = combine(_searchQuery, _tabIndex) { query, tab ->
        Pair(query, tab)
    }.flatMapLatest { (query, tab) ->
        if (query.isNotBlank()) {
            repository.searchPosts(query)
        } else {
            when (tab) {
                1 -> repository.observeUnreadPosts()
                2 -> repository.observeFavoritePosts()
                else -> repository.observeAllPosts()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            smartCollectionEngine.seedDefaultProCollectionsIfEmpty()
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

    fun toggleFavorite(item: ItemDetail) {
        viewModelScope.launch {
            repository.toggleFavoriteStatus(item.item.id, !item.isFavorite)
        }
    }

    suspend fun unlockWithPasscode(passcode: String): Boolean {
        return settingsRepository.verifyAndUnlockWithPasscode(passcode)
    }

    fun saveAiConfig(apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            if (apiKey.isNotBlank()) settingsRepository.setOpenAiApiKey(apiKey)
            settingsRepository.setCustomBaseUrl(baseUrl)
            settingsRepository.setSelectedModel(model)
        }
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

        return res.map { (analysis, _) ->
            val summaryText = analysis.summary
            repository.savePost(
                canonicalUrl = item.item.canonicalUrl,
                shortcode = item.item.shortcode,
                authorHandle = item.item.authorHandle,
                bodyText = item.displayBody,
                commentsText = item.item.commentsText,
                manualNote = item.manualNote,
                manualSummary = summaryText,
                categoryId = item.userEdit?.categoryId
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTab by viewModel.tabIndex.collectAsState()
    val isPro by viewModel.isProUnlocked.collectAsState()
    val collections by viewModel.smartCollections.collectAsState()

    var selectedItemForDetail by remember { mutableStateOf<ItemDetail?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    if (selectedItemForDetail != null) {
        DetailDialog(
            item = selectedItemForDetail!!,
            viewModel = viewModel,
            onDismiss = { selectedItemForDetail = null }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onOpenUnlock = {
                showSettingsDialog = false
                showUnlockDialog = true
            }
        )
    }

    if (showUnlockDialog) {
        PasscodeUnlockDialog(
            viewModel = viewModel,
            onDismiss = { showUnlockDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Tabs
            PrimaryTabRow(selectedTabIndex = currentTab) {
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
                    text = { Text(if (isPro) "智慧分類" else "智慧(PRO)") }
                )
            }

            // Tab 3: Pro Smart Collections or General Post list
            if (currentTab == 3) {
                if (!isPro) {
                    // Pro gate prompt
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = Color(0xFFFFB300)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Pro 專屬智慧策展",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "解鎖後即可使用智慧規則過濾、高讚熱門歸納、自動標籤與進階回顧。\n無需註冊帳號，輸入通行碼即可終身啟用！",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = { showUnlockDialog = true }) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("輸入通行密碼解鎖")
                            }
                        }
                    }
                } else {
                    // Pro Smart Collections view
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "您的智慧篩選條件",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
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
                }
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
                        lineHeight = 22.sp
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
                            onClick = { selectedItemForDetail = itemDetail },
                            onToggleRead = { viewModel.toggleRead(itemDetail) },
                            onToggleFavorite = { viewModel.toggleFavorite(itemDetail) }
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
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (itemDetail.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${itemDetail.item.authorHandle.ifBlank { "threads_user" }}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row {
                    IconButton(
                        onClick = onToggleRead,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (itemDetail.isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Read",
                            tint = if (itemDetail.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (itemDetail.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Favorite",
                            tint = if (itemDetail.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = itemDetail.displayBody.ifBlank { "無內文" },
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (itemDetail.manualSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "AI 摘要: ${itemDetail.manualSummary}",
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
        }
    }
}

@Composable
fun DetailDialog(
    item: ItemDetail,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isSummarizing by remember { mutableStateOf(false) }
    var currentSummary by remember { mutableStateOf(item.manualSummary) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .androidx.compose.foundation.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "@${item.item.authorHandle}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = {
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
                        },
                        enabled = !isSummarizing
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI 摘要", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = item.displayBody,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )

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
                            Text(text = c.text, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("關閉")
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onOpenUnlock: () -> Unit
) {
    val context = LocalContext.current
    val currentBaseUrl by viewModel.customBaseUrl.collectAsState()
    val currentModel by viewModel.selectedModel.collectAsState()
    val isPro by viewModel.isProUnlocked.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf(currentBaseUrl) }
    var modelInput by remember { mutableStateOf(currentModel) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .androidx.compose.foundation.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(
                    text = "系統設定 (BYOK)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "API Key (支援 OpenAI / 相容端點)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "API Base URL (OpenAI-compatible 端點)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    placeholder = { Text("https://api.openai.com/v1 或自訂代理") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Model ID", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    placeholder = { Text("gpt-5-nano, deepseek-chat 等") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPro) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPro) "👑 Pro 永久版已啟用" else "免費版 (未啟用 Pro)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isPro) "您享有智慧規則、完整備份與 Widget 全開" else "點擊輸入通行碼解鎖完整功能",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (!isPro) {
                            Button(onClick = onOpenUnlock) {
                                Text("解鎖", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("關閉")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = {
                        viewModel.saveAiConfig(apiKeyInput, baseUrlInput, modelInput)
                        Toast.makeText(context, "設定已儲存", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) {
                        Text("儲存")
                    }
                }
            }
        }
    }
}

@Composable
fun PasscodeUnlockDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var passcode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "解鎖 Reater Pro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "本 App 無需註冊/綁定任何帳號。\n請輸入購買或獲贈的通行密碼 (如: REATER_PRO_2026)，驗證通過後即可永久開通。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = passcode,
                    onValueChange = {
                        passcode = it
                        errorMsg = ""
                    },
                    placeholder = { Text("輸入解鎖通行碼...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    isError = errorMsg.isNotBlank()
                )

                if (errorMsg.isNotBlank()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = {
                        coroutineScope.launch {
                            val success = viewModel.unlockWithPasscode(passcode)
                            if (success) {
                                Toast.makeText(context, "🎉 成功開通 Reater Pro！", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                errorMsg = "通行碼無效或格式錯誤，請再試一次"
                            }
                        }
                    }) {
                        Text("確認開通")
                    }
                }
            }
        }
    }
}
