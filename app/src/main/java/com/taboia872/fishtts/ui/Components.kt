package com.taboia872.fishtts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taboia872.fishtts.fish.FishApi
import com.taboia872.fishtts.settings.AppSettings
import com.taboia872.fishtts.settings.AppSettings.config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------- test card

@Composable
fun TestCard(
    playerState: LocalPlayerState,
    onSpeak: (String) -> Unit,
    onStop: () -> Unit
) {
    val cfg by config.collectAsState()
    var text by rememberSaveable { mutableStateOf("Olá! Esta é a voz do FishTTS.") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Testar voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                cfg.fishVoiceName.ifBlank { "Voz padrão do FishAudio" } +
                    " • " + cfg.fishModel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Digite um texto…") },
                minLines = 2,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (playerState) {
                    is LocalPlayerState.Playing -> {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Default.Stop, "Parar")
                        }
                    }
                    is LocalPlayerState.Synthesizing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {}
                }
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { onSpeak(text) },
                    enabled = text.isNotBlank() &&
                        playerState !is LocalPlayerState.Synthesizing
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Falar")
                }
            }
        }
    }
}

// ---------------------------------------------------------- model picker

@Composable
fun ModelPicker() {
    val cfg by config.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Modelo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (cfg.fishModel == "s2.1-pro-free") "Grátis (tier de desenvolvimento)"
                else "Pago (cobrança por uso)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FishApi.MODELS.forEach { m ->
                    FilterChip(
                        selected = cfg.fishModel == m.id,
                        onClick = { AppSettings.update { it.copy(fishModel = m.id) } },
                        label = { Text(m.id) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------- favorites card

@Composable
fun FavoritesCard() {
    val cfg by config.collectAsState()
    val favorites = AppSettings.favoriteList()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Favoritos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            if (favorites.isEmpty()) {
                Text(
                    "Nenhuma voz favorita ainda. Busque abaixo e toque no coração para salvar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                favorites.forEach { (id, title) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppSettings.update {
                                    it.copy(fishVoiceId = id, fishVoiceName = title)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (id == cfg.fishVoiceId)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (id == cfg.fishVoiceId) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { AppSettings.toggleFavorite(id, title) }) {
                            Icon(
                                Icons.Default.Favorite,
                                "Remover favorito",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------- voice search

@Composable
fun VoiceSearchCard() {
    val cfg by config.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FishApi.FishVoice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Buscar vozes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex.: goku, narrador, brasileira…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            page = 1
                            val res = withContext(Dispatchers.IO) {
                                FishApi.listVoices(cfg.apiKey, query, pageNumber = 1)
                            }
                            res.onSuccess {
                                results = it.voices
                                hasMore = it.hasMore
                            }.onFailure {
                                error = it.message
                                results = emptyList()
                                hasMore = false
                            }
                            loading = false
                        }
                    },
                    enabled = !loading
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Buscar")
                }
                if (hasMore) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                val next = page + 1
                                val res = withContext(Dispatchers.IO) {
                                    FishApi.listVoices(cfg.apiKey, query, pageNumber = next)
                                }
                                res.onSuccess {
                                    results = results + it.voices
                                    hasMore = it.hasMore
                                    page = next
                                }.onFailure { error = it.message }
                                loading = false
                            }
                        },
                        enabled = !loading
                    ) { Text("Mais") }
                }
            }
            if (loading) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                results.forEach { voice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppSettings.update {
                                    it.copy(
                                        fishVoiceId = voice.id,
                                        fishVoiceName = voice.title
                                    )
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            voice.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (voice.id == cfg.fishVoiceId)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (voice.id == cfg.fishVoiceId) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint =MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = {
                            AppSettings.toggleFavorite(voice.id, voice.title)
                        }) {
                            if (AppSettings.isFavorite(voice.id)) {
                                Icon(
                                    Icons.Default.Favorite,
                                    "Remover favorito",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.FavoriteBorder,
                                    "Adicionar favorito",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------- API key card

@Composable
fun ApiKeyCard() {
    val cfg by config.collectAsState()
    var key by rememberSaveable(cfg.apiKey) { mutableStateOf(cfg.apiKey) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Chave de API FishAudio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cole a chave aqui") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { AppSettings.update { it.copy(apiKey = key.trim()) } },
                    enabled = key.isNotBlank() && key.trim() != cfg.apiKey
                ) { Text("Salvar") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Obtenha em fish.audio → Profile → API Key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/** LocalPlayer.State alias so UI files need fewer imports. */
typealias LocalPlayerState = com.taboia872.fishtts.tts.LocalPlayer.State
