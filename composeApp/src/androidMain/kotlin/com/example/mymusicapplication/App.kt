package com.example.mymusicapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mymusicapplication.music.AudioTrack
import com.example.mymusicapplication.music.PlaybackController
import com.example.mymusicapplication.music.PlaybackMode
import com.example.mymusicapplication.music.PlaybackUiState
import kotlinx.coroutines.launch

@Composable
fun App(
    controller: PlaybackController,
    onStartPlaybackService: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    var pendingSeekPosition by remember(state.currentTrack?.id, state.progressMs) { mutableStateOf(state.progressMs.toFloat()) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            controller.refreshLibrary()
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("我的音乐", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
//            Text("KMP 共享状态 + Android 原生扫描 / MediaPlayer / 后台通知", color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!hasPermission) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("需要音频读取权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("授权后会扫描本地音乐并建立收藏列表。")
                        Button(onClick = { launcher.launch(requiredPermission) }) {
                            Text("授权并扫描")
                        }
                    }
                }
            }

            CurrentTrackCard(
                state = state,
                pendingSeekPosition = pendingSeekPosition,
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

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(message, modifier = Modifier.weight(1f))
                        TextButton(onClick = controller::clearError) { Text("关闭") }
                    }
                }
            }

            TrackList(
                tracks = if (favoritesOnly) state.favorites else state.library,
                currentTrackId = state.currentTrack?.id,
                onToggleFavorite = { track ->
                    scope.launch { controller.toggleFavorite(track.id) }
                },
                onPlayTrack = { track ->
                    onStartPlaybackService()
                    scope.launch { controller.playTrack(track.id) }
                },
            )
        }
    }
}

@Composable
private fun CurrentTrackCard(
    state: PlaybackUiState,
    pendingSeekPosition: Float,
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
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(currentTrack?.title ?: "尚未开始播放", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(currentTrack?.artist ?: "从下方音乐库选择歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("进度 ${formatDuration(state.progressMs)} / ${formatDuration(state.durationMs)}")
            Slider(
                value = pendingSeekPosition.coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
                onValueChange = onPendingSeekChange,
                valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                onValueChangeFinished = onSeekCommit,
                enabled = currentTrack != null,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onPrevious, enabled = currentTrack != null) { Text("上一曲") }
                Button(onClick = onPlayPause, enabled = currentTrack != null || state.library.isNotEmpty()) {
                    Text(if (state.isPlaying) "暂停" else "播放")
                }
                Button(onClick = onNext, enabled = currentTrack != null || state.library.isNotEmpty()) { Text("下一曲") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("播放模式：${modeLabel(state.playbackMode)}")
                TextButton(onClick = onModeChange) { Text("切换") }
            }

            Text("音量 ${(state.volume * 100).toInt()}%")
            Slider(value = state.volume, onValueChange = onVolumeChange, valueRange = 0f..1f)

            Text("倍速 ${String.format("%.1f", state.speed)}x")
            Slider(value = state.speed, onValueChange = onSpeedChange, valueRange = 0.5f..2f)
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<AudioTrack>,
    currentTrackId: String?,
    onToggleFavorite: (AudioTrack) -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
) {
    if (tracks.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "暂无歌曲，请先授权并点击重新扫描。",
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { track ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayTrack(track) }
                    .padding(vertical = 12.dp),
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
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
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