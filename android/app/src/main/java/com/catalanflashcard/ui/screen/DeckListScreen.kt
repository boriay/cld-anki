package com.catalanflashcard.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.catalanflashcard.R
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.WeatherState
import com.catalanflashcard.ui.WeatherStrip
import com.catalanflashcard.ui.resolveAppLanguage
import com.catalanflashcard.ui.viewmodel.DeckViewModel
import kotlinx.coroutines.delay

// Цвет «авто-синк включён». Зелёный читается как индикатор активности и на
// светлом, и на тёмном тулбаре, не завязываясь на акцент темы.
private val SyncOnColor = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: DeckViewModel,
    weather: WeatherState,
    onDeckClick: (String) -> Unit,
    onAddDeckClick: () -> Unit
) {
    val decks by viewModel.decks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var deckToDelete by remember { mutableStateOf<Deck?>(null) }

    // Плашка-подсказка про синк не висит постоянно: показывается при старте и
    // при тапе по кнопке синка, затем сама гаснет через 10 секунд. Каждый тап
    // увеличивает триггер и перезапускает таймер.
    var syncHintTrigger by remember { mutableStateOf(0) }
    var syncHintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(syncHintTrigger) {
        syncHintVisible = true
        delay(10_000)
        syncHintVisible = false
    }

    // Resolve the deck-filter language from the active locale. LocalConfiguration
    // recomposes on a locale change, so switching language re-filters the list.
    val currentLanguage = resolveAppLanguage(LocalConfiguration.current.locales[0])
    LaunchedEffect(currentLanguage) { viewModel.setLanguage(currentLanguage) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    deckToDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { deckToDelete = null },
            title = { Text(stringResource(R.string.delete_deck_confirm_title)) },
            text = { Text(stringResource(R.string.delete_deck_confirm_message, deck.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDeck(deck.id)
                        deckToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deckToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        // Transparent so the app-wide weather background shows through the list
        // area; the top bar and cards keep their own surface for readability.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deck_title)) },
                actions = {
                    LanguageMenu()
                    // Тумблер авто-синка, он же индикатор. Тап переключает вкл/выкл
                    // (включение принудительно запускает синк); во время синка крутится.
                    // stateDescription + Role.Switch озвучивают состояние для TalkBack,
                    // в т.ч. когда вместо иконки крутится спиннер без contentDescription.
                    val syncStateDescription = when {
                        isSyncing -> stringResource(R.string.sync_in_progress)
                        autoSyncEnabled -> stringResource(R.string.sync_auto_on)
                        else -> stringResource(R.string.sync_auto_off)
                    }
                    IconButton(
                        onClick = {
                            viewModel.toggleAutoSync()
                            syncHintTrigger++
                        },
                        modifier = Modifier.semantics {
                            role = Role.Switch
                            stateDescription = syncStateDescription
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Sync,
                                contentDescription = stringResource(
                                    if (autoSyncEnabled) R.string.sync_auto_on else R.string.sync_auto_off
                                ),
                                // Цветом показываем состояние: вкл — зелёный, выкл — приглушённо.
                                tint = if (autoSyncEnabled) {
                                    SyncOnColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDeckClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_deck))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            WeatherStrip(
                state = weather,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
            // Подсказка про синк всегда занимает своё место в Column (видимость через
            // alpha, а не через AnimatedVisibility), поэтому при скрытии её место
            // остаётся пустым — список под ней не прыгает и не перекрывается.
            SyncHint(visible = syncHintVisible)
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (isLoading && decks.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (decks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_decks),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(decks) { deck ->
                            DeckListItem(
                                deck = deck,
                                onDeckClick = onDeckClick,
                                onDeleteClick = { deckToDelete = deck }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Плашка-подсказка про серверный синк. Всегда присутствует в layout, видимость
// меняется через alpha — так зарезервированное под неё место остаётся пустым при
// скрытии, и список не сдвигается. Маленькая иконка синка связывает текст с
// кнопкой в тулбаре.
@Composable
private fun SyncHint(visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "syncHintAlpha"
    )
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Filled.Sync,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.sync_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Overflow menu that switches the in-app language via the AppCompat per-app
// locale API. The system stores the choice (autoStoreLocales) and recreates the
// Activity, so the UI strings and the deck-list filter both follow the selection.
@Composable
private fun LanguageMenu() {
    var expanded by remember { mutableStateOf(false) }

    fun applyLocale(tags: LocaleListCompat) {
        AppCompatDelegate.setApplicationLocales(tags)
        expanded = false
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Language, contentDescription = stringResource(R.string.language))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_system_default)) },
                onClick = { applyLocale(LocaleListCompat.getEmptyLocaleList()) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_english)) },
                onClick = { applyLocale(LocaleListCompat.forLanguageTags("en")) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_spanish)) },
                onClick = { applyLocale(LocaleListCompat.forLanguageTags("es")) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_russian)) },
                onClick = { applyLocale(LocaleListCompat.forLanguageTags("ru")) }
            )
        }
    }
}

@Composable
fun DeckListItem(
    deck: Deck,
    onDeckClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDeckClick(deck.id) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deck.name,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}
