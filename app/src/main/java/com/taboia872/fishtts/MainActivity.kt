package com.taboia872.fishtts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.taboia872.fishtts.settings.AppSettings
import com.taboia872.fishtts.tts.LocalPlayer
import com.taboia872.fishtts.ui.ApiKeyCard
import com.taboia872.fishtts.ui.FavoritesCard
import com.taboia872.fishtts.ui.ModelPicker
import com.taboia872.fishtts.ui.TestCard
import com.taboia872.fishtts.ui.VoiceSearchCard
import com.taboia872.fishtts.ui.theme.FishTtsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FishTtsTheme {
                MainScreen(
                    onOpenEngineSettings = {
                        runCatching {
                            startActivity(
                                Intent("com.android.settings.TTS_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onOpenEngineSettings: () -> Unit) {
    val cfg by AppSettings.config.collectAsState()
    val playerState by LocalPlayer.state.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FishTTS") },
                actions = {
                    IconButton(onClick = onOpenEngineSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configurações do motor TTS"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { TestCard(
                playerState = playerState,
                onSpeak = { text ->
                    scope.launch {
                        LocalPlayer.speak(
                            apiKey = cfg.apiKey,
                            text = text,
                            model = cfg.fishModel,
                            voiceId = cfg.fishVoiceId
                        )
                    }
                },
                onStop = { LocalPlayer.stop() }
            ) }
            item { ModelPicker() }
            item { FavoritesCard() }
            item { VoiceSearchCard() }
            item { ApiKeyCard() }
        }
    }
}
