package com.sagadrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sagadrive.app.playback.MediaControllerManager
import com.sagadrive.app.ui.DashboardScreen
import com.sagadrive.app.ui.theme.SagaTheme

class MainActivity : ComponentActivity() {
    private lateinit var media: MediaControllerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = MediaControllerManager(applicationContext)
        setContent { SagaTheme { DashboardScreen(media) } }
    }

    override fun onStart() { super.onStart(); media.connect() }
    override fun onStop() { media.disconnect(); super.onStop() }
}
