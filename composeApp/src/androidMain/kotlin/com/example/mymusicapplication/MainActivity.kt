package com.example.mymusicapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import com.example.mymusicapplication.music.PlaybackController
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val playbackController: PlaybackController by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                controller = playbackController,
                onStartPlaybackService = { PlaybackService.start(this) },
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    MaterialTheme {
        Text("音乐播放器预览需要运行时依赖")
    }
}