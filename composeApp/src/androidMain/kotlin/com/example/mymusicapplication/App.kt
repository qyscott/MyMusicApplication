package com.example.mymusicapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mymusicapplication.music.AudioTrack
import com.example.mymusicapplication.music.PlaybackController
import com.example.mymusicapplication.music.PlaybackMode
import com.example.mymusicapplication.music.PlaybackUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun App(
    controller: PlaybackController,
    onStartPlaybackService: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requiredPermission = remember { audioPermission() }
    var hasPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    val state by controller.uiState.collectAsState()
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingSeekPosition by remember(state.currentTrack?.id, state.progressMs) {
        mutableStateOf(state.progressMs.toFloat())
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            controller.refreshLibrary()
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .padding(bottom = MiniPlayerPeekHeight + 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("我的音乐", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                if (!hasPermission) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("需要音频读取权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("授权后会扫描本地音乐并建立收藏列表。")
                            Button(onClick = { launcher.launch(requiredPermission) }) {
                                Text("授权并扫描")
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { favoritesOnly = false }, enabled = favoritesOnly) {
                        Text("音乐库")
                    }
                    Button(onClick = { favoritesOnly = true }, enabled = !favoritesOnly) {
                        Text("收藏")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { controller.refreshLibrary() } }) {
                        Text(if (state.isRefreshingLibrary) "扫描中..." else "重新扫描")
                    }
                }

                state.errorMessage?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(message, modifier = Modifier.weight(1f))
                            TextButton(onClick = controller::clearError) {
                                Text("关闭")
                            }
                        }
                    }
                }

                TrackList(
                    modifier = Modifier.weight(1f),
                    tracks = if (favoritesOnly) state.favorites else state.library,
                    currentTrackId = state.currentTrack?.id,
                    listState = listState,
                    onToggleFavorite = { track ->
                        scope.launch { controller.toggleFavorite(track.id) }
                    },
                    onPlayTrack = { track ->
                        onStartPlaybackService()
                        playerExpanded = true
                        scope.launch { controller.playTrack(track.id) }
                    },
                )
            }

            BottomPlayerDrawer(
                modifier = Modifier.align(Alignment.BottomCenter),
                state = state,
                expanded = playerExpanded,
                pendingSeekPosition = pendingSeekPosition,
                onExpandedChange = { playerExpanded = it },
                onPendingSeekChange = { pendingSeekPosition = it },
                onSeekCommit = {
                    scope.launch { controller.seekTo(pendingSeekPosition.toLong()) }
                },
                onPlayPause = {
                    onStartPlaybackService()
                    scope.launch { controller.togglePlayPause() }
                },
                onPrevious = {
                    onStartPlaybackService()
                    scope.launch { controller.playPrevious() }
                },
                onNext = {
                    onStartPlaybackService()
                    scope.launch { controller.playNext() }
                },
                onModeChange = { controller.setPlaybackMode(nextMode(state.playbackMode)) },
                onVolumeChange = controller::setVolume,
                onSpeedChange = controller::setPlaybackSpeed,
            )
        }
    }
}

@Composable
private fun BottomPlayerDrawer(
    modifier: Modifier = Modifier,
    state: PlaybackUiState,
    expanded: Boolean,
    pendingSeekPosition: Float,
    onExpandedChange: (Boolean) -> Unit,
    onPendingSeekChange: (Float) -> Unit,
    onSeekCommit: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onModeChange: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    val currentTrack = state.currentTrack
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var settleExpanded by remember { mutableStateOf<Boolean?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxDrawerHeight = maxHeight * 0.78f
        val sheetHeight = minOf(MiniPlayerExpandedHeight, maxDrawerHeight).coerceAtLeast(MiniPlayerPeekHeight)
        val collapsedOffsetPx = with(density) { (sheetHeight - MiniPlayerPeekHeight).toPx() }
        val effectiveExpanded = settleExpanded ?: expanded

        val animatedOffsetPx by animateFloatAsState(
            targetValue = if (effectiveExpanded) 0f else collapsedOffsetPx,
            animationSpec = spring(),
            label = "bottom_player_drawer_offset",
        )

        LaunchedEffect(expanded, settleExpanded) {
            if (settleExpanded != null && settleExpanded == expanded) {
                settleExpanded = null
            }
        }

        LaunchedEffect(expanded, collapsedOffsetPx, dragging, settleExpanded) {
            if (!dragging && settleExpanded == null) {
                dragOffsetPx = if (expanded) 0f else collapsedOffsetPx
            }
        }

        var currentOffsetPx = if (dragging) dragOffsetPx else animatedOffsetPx
        var progress = if (collapsedOffsetPx <= 0f) 1f else {
            (1f - currentOffsetPx / collapsedOffsetPx).coerceIn(0f, 1f)
        }

        fun updateDrag(dragAmount: Float) {
            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, collapsedOffsetPx)
            currentOffsetPx = if (dragging) dragOffsetPx else animatedOffsetPx
            progress = (1f - currentOffsetPx / collapsedOffsetPx).coerceIn(0f, 1f)

        }

        fun finishDrag() {
            val targetExpanded = progress >= DragExpandThreshold
            settleExpanded = targetExpanded
            dragging = false
            onExpandedChange(targetExpanded)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, currentOffsetPx.roundToInt()) },
            shape = MaterialTheme.shapes.large,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.pointerInput(expanded, collapsedOffsetPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragOffsetPx = currentOffsetPx
                            },
                            onVerticalDrag = { _, dragAmount ->
                                updateDrag(dragAmount)
                            },
                            onDragEnd = { finishDrag() },
                            onDragCancel = {
                                finishDrag()
                            },
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                            .width(36.dp)
                            .height(4.dp)
                            .clickable { onExpandedChange(!expanded) },
                    )

                    MiniPlayerBar(
                        state = state,
                        onToggleExpanded = { onExpandedChange(!expanded) },
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .alpha(progress),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            currentTrack?.title ?: "尚未开始播放",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            currentTrack?.artist ?: "从下方音乐库选择歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text("进度 ${formatDuration(state.progressMs)} / ${formatDuration(state.durationMs)}")
                        Slider(
                            value = pendingSeekPosition.coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
                            onValueChange = onPendingSeekChange,
                            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                            onValueChangeFinished = onSeekCommit,
                            enabled = currentTrack != null && progress > 0.95f,
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = onPrevious, enabled = currentTrack != null && progress > 0.95f) {
                                Text("上一曲")
                            }
                            Button(
                                onClick = onPlayPause,
                                enabled = (currentTrack != null || state.library.isNotEmpty()) && progress > 0.95f,
                            ) {
                                Text(if (state.isPlaying) "暂停" else "播放")
                            }
                            Button(
                                onClick = onNext,
                                enabled = (currentTrack != null || state.library.isNotEmpty()) && progress > 0.95f,
                            ) {
                                Text("下一曲")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("播放模式：${modeLabel(state.playbackMode)}")
                            TextButton(onClick = onModeChange, enabled = progress > 0.95f) {
                                Text("切换")
                            }
                        }

                        Text("音量 ${(state.volume * 100).toInt()}%")
                        Slider(
                            value = state.volume,
                            onValueChange = onVolumeChange,
                            valueRange = 0f..1f,
                            enabled = progress > 0.95f,
                        )

                        Text("倍速 ${"%.1f".format(state.speed)}x")
                        Slider(
                            value = state.speed,
                            onValueChange = onSpeedChange,
                            valueRange = 0.5f..2f,
                            enabled = progress > 0.95f,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackList(
    modifier: Modifier = Modifier,
    tracks: List<AudioTrack>,
    currentTrackId: String?,
    listState: LazyListState,
    onToggleFavorite: (AudioTrack) -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
) {
    if (tracks.isEmpty()) {
        Card(modifier = modifier.fillMaxWidth()) {
            Text(
                text = "暂无歌曲，请先授权并点击重新扫描。",
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(tracks, key = { it.id }) { track ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(track) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = if (track.id == currentTrackId) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = listOfNotNull(track.artist, track.album).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatDuration(track.durationMs))
                            TextButton(onClick = { onToggleFavorite(track) }) {
                                Text(if (track.isFavorite) "取消收藏" else "收藏")
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        if (tracks.size > 1) {
            ListScrollbar(
                listState = listState,
                itemCount = tracks.size,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    state: PlaybackUiState,
    onToggleExpanded: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val currentTrack = state.currentTrack
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (state.isPlaying) "♪" else "♫", style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(currentTrack?.title ?: "尚未开始播放", fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                text = currentTrack?.artist ?: "点击展开播放器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        MiniPlayerAction(enabled = currentTrack != null, text = "◀", onClick = onPrevious)
        MiniPlayerAction(
            enabled = currentTrack != null || state.library.isNotEmpty(),
            text = if (state.isPlaying) "❚❚" else "▶",
            onClick = onPlayPause,
        )
        MiniPlayerAction(
            enabled = currentTrack != null || state.library.isNotEmpty(),
            text = "▶▶",
            onClick = onNext,
        )
    }
}

@Composable
private fun RowScope.MiniPlayerAction(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(text)
    }
}

@Composable
private fun ListScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var containerHeightPx by remember { mutableStateOf(0) }
    val scrollbarTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val maxStartIndex = (itemCount - visibleCount).coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / itemCount.toFloat()).coerceIn(0.12f, 1f)
    val thumbOffsetFraction = (listState.firstVisibleItemIndex.toFloat() / maxStartIndex.toFloat()).coerceIn(0f, 1f)

    fun scrollToOffset(y: Float) {
        if (containerHeightPx <= 0 || itemCount <= 1) return
        val fraction = (y / containerHeightPx.toFloat()).coerceIn(0f, 1f)
        val targetIndex = (fraction * (itemCount - 1)).roundToInt()
        scope.launch {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .width(16.dp)
            .onSizeChanged { containerHeightPx = it.height }
            .pointerInput(itemCount, containerHeightPx) {
                detectDragGestures(
                    onDragStart = { offset -> scrollToOffset(offset.y) },
                    onDrag = { change, _ ->
                        scrollToOffset(change.position.y)
                    },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackX = size.width / 2f
            drawLine(
                color = scrollbarTrackColor,
                start = Offset(trackX, 0f),
                end = Offset(trackX, size.height),
                strokeWidth = 4.dp.toPx(),
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((containerHeightPx * (1f - thumbFraction)) * thumbOffsetFraction).roundToInt(),
                    )
                }
                .width(10.dp)
                .height((containerHeightPx * thumbFraction).coerceAtLeast(48f).dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun modeLabel(mode: PlaybackMode): String = when (mode) {
    PlaybackMode.SEQUENTIAL -> "顺序播放"
    PlaybackMode.REPEAT_ONE -> "单曲循环"
    PlaybackMode.SHUFFLE -> "随机播放"
}

private fun nextMode(mode: PlaybackMode): PlaybackMode = when (mode) {
    PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
    PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
    PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENTIAL
}

private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

@Preview
@Composable
private fun AppPreview() {
    MaterialTheme {
        Text("需要在真机或模拟器中查看音乐播放界面")
    }
}

private val MiniPlayerPeekHeight = 88.dp
private val MiniPlayerExpandedHeight = 520.dp
private const val DragExpandThreshold = 0.35f
