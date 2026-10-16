package com.taboia872.fishtts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import com.taboia872.fishtts.tts.LocalPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------- test card

@Composable
fun TestCard(
    playerState: LocalPlayer.State,
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
                    is LocalPlayer.State.Playing -> {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Default.Stop, "Parar")
                        }
                    }
                    is LocalPlayer.State.Synthesizing -> {
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
                        playerState !is LocalPlayer.State.Synthesizing
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
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Modelo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    cfg.fishModel + if (cfg.fishModel == "s2.1-pro-free") " (grátis)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                FishApi.MODELS.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                m.id + if (m.free) " — grátis" else ""
                            )
                        },
                        onClick = {
                            AppSettings.update { it.copy(fishModel = m.id) }
                            expanded = false
                        },
                        trailingIcon = if (cfg.fishModel == m.id) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
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

private enum class LangFilter { ALL, PT, EN, OTHER }

@Composable
fun VoiceSearchCard() {
    val cfg by config.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FishApi.FishVoice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var langFilter by remember { mutableStateOf(LangFilter.ALL) }
    var lastMode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // One loader for both modes. mode="popular" ignores `query`.
    fun load(nextPage: Int, mode: String) {
        scope.launch {
            loading = true
            error = null
            val res = withContext(Dispatchers.IO) {
                if (mode == "popular") {
                    FishApi.listVoices(
                        cfg.apiKey, pageNumber = nextPage, sortBy = "score"
                    )
                } else {
                    FishApi.listVoices(
                        cfg.apiKey, search = query, pageNumber = nextPage
                    )
                }
            }
            res.onSuccess {
                results = if (nextPage <= 1 || mode != lastMode) it.voices
                         else results + it.voices
                hasMore = it.hasMore
                page = nextPage
                lastMode = mode
            }.onFailure {
                error = it.message
                if (nextPage <= 1 || mode != lastMode) {
                    results = emptyList()
                    hasMore = false
                }
            }
            loading = false
        }
    }

    val visible = when (langFilter) {
        LangFilter.ALL -> results
        LangFilter.PT -> results.filter { v ->
            v.languages.any { it.equals("pt", true) || it.startsWith("pt-", true) }
        }
        LangFilter.EN -> results.filter { v ->
            v.languages.any { it.equals("en", true) || it.startsWith("en-", true) }
        }
        LangFilter.OTHER -> results.filter { v ->
            v.languages.none { it.equals("pt", true) || it.startsWith("pt-", true) } &&
            v.languages.none { it.equals("en", true) || it.startsWith("en-", true) }
        }
    }

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
                    onClick = { load(1, "search") },
                    enabled = !loading && query.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Buscar")
                }
                OutlinedButton(
                    onClick = {
                        val next = if (lastMode == "popular" && page > 0) page + 1 else 1
                        load(next, "popular")
                    },
                    enabled = !loading && !(lastMode == "popular" && !hasMore && page > 0)
                ) {
                    Text(if (lastMode == "popular" && page > 0) "Mais populares" else "Populares")
                }
                if (lastMode == "search" && hasMore) {
                    OutlinedButton(
                        onClick = { load(page + 1, "search") },
                        enabled = !loading
                    ) {
                        Text("Mais")
                    }
                }
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = langFilter == LangFilter.ALL,
                        onClick = { langFilter = LangFilter.ALL },
                        label = { Text("Todas") }
                    )
                    FilterChip(
                        selected = langFilter == LangFilter.PT,
                        onClick = { langFilter = LangFilter.PT },
                        label = { Text("Português") }
                    )
                    FilterChip(
                        selected = langFilter == LangFilter.EN,
                        onClick = { langFilter = LangFilter.EN },
                        label = { Text("Inglês") }
                    )
                    FilterChip(
                        selected = langFilter == LangFilter.OTHER,
                        onClick = { langFilter = LangFilter.OTHER },
                        label = { Text("Outros") }
                    )
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
            if (visible.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                visible.forEach { voice ->
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
                                tint = MaterialTheme.colorScheme.primary,
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

