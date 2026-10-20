package com.reater.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.reater.app.data.local.entity.MediaEntity
import com.reater.app.ui.player.rememberMediaImageLoader
import com.reater.app.ui.player.InlineVideoPlayer
import java.io.File
import kotlinx.coroutines.flow.drop

/** 固定預覽比例，載入/錯誤不改變內容高度；完整內容交由全螢幕 Viewer。 */
@Composable
internal fun DetailMediaPager(
    media: List<MediaEntity>,
    selectedUrl: String,
    onPageChanged: (String) -> Unit,
    onScrollChanged: (Boolean) -> Unit,
    onOpen: (MediaEntity) -> Unit
) {
    if (media.isEmpty()) return
    val pager = rememberPagerState(initialPage = media.indexOfFirst { it.remoteUrl == selectedUrl }.coerceAtLeast(0)) { media.size }
    val callback by rememberUpdatedState(onPageChanged)
    val currentMedia by rememberUpdatedState(media)
    LaunchedEffect(selectedUrl) {
        val target = media.indexOfFirst { it.remoteUrl == selectedUrl }
        if (target >= 0 && target != pager.currentPage) pager.scrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { page ->
            currentMedia.getOrNull(page)?.let { callback(it.remoteUrl) }
        }
    }
    LaunchedEffect(pager.isScrollInProgress) { onScrollChanged(pager.isScrollInProgress) }
    DisposableEffect(Unit) { onDispose { onScrollChanged(false) } }
    Column {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { index ->
            val entry = media[index]
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onOpen(entry) }, contentAlignment = Alignment.Center) {
                if (entry.kind.equals("VIDEO", ignoreCase = true)) {
                    InlineVideoPlayer(
                        remoteUrl = entry.remoteUrl,
                        localPath = entry.localPath,
                        autoPlay = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    var retry by remember(entry.remoteUrl) { mutableIntStateOf(0) }
                    SubcomposeAsyncImage(
                        imageLoader = rememberMediaImageLoader(),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(if (entry.localPath.isNotBlank() && File(entry.localPath).exists()) File(entry.localPath) else entry.remoteUrl)
                            .setParameter("retry", retry).build(),
                        contentDescription = "圖片 ${index + 1} / ${media.size}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                        error = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("載入失敗")
                                TextButton(onClick = { retry++ }) { Text("重新載入") }
                            }
                        },
                        success = { SubcomposeAsyncImageContent() }
                    )
                }
            }
        }
        if (media.size > 1) Text("${pager.currentPage + 1} / ${media.size}",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
