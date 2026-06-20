package com.catalanflashcard.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catalanflashcard.R
import com.catalanflashcard.data.entity.Card as CardEntity
import com.catalanflashcard.ui.viewmodel.DeckViewModel
import androidx.compose.material3.AlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId: String,
    deckViewModel: DeckViewModel,
    onBackClick: () -> Unit,
    onStudyClick: (String) -> Unit
) {
    val currentDeck by deckViewModel.selectedDeck.collectAsState()
    val isLoadingDeck by deckViewModel.isLoadingDeck.collectAsState()
    val cardCount by deckViewModel.selectedDeckCardCount.collectAsState()
    val dueCount by deckViewModel.selectedDeckDueCount.collectAsState()
    val cards by deckViewModel.selectedDeckCards.collectAsState()
    val error by deckViewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddCard by remember { mutableStateOf(false) }
    var cardToEdit by remember { mutableStateOf<CardEntity?>(null) }
    var cardToDelete by remember { mutableStateOf<CardEntity?>(null) }

    LaunchedEffect(deckId) {
        deckViewModel.loadDeckStats(deckId)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            deckViewModel.clearError()
        }
    }

    DisposableEffect(deckId) {
        onDispose {
            deckViewModel.clearDeckStats()
        }
    }

    if (showAddCard) {
        CardEditorDialog(
            isEdit = false,
            onConfirm = { front, back -> deckViewModel.createCard(deckId, front, back) },
            onDismiss = { showAddCard = false }
        )
    }

    cardToEdit?.let { card ->
        CardEditorDialog(
            isEdit = true,
            initialFront = card.front,
            initialBack = card.back,
            onConfirm = { front, back -> deckViewModel.updateCard(card, front, back) },
            onDismiss = { cardToEdit = null }
        )
    }

    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text(stringResource(R.string.delete_card_confirm_title)) },
            text = { Text(stringResource(R.string.delete_card_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deckViewModel.deleteCard(card)
                        cardToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(currentDeck?.name ?: stringResource(R.string.deck_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentDeck != null) {
                FloatingActionButton(onClick = { showAddCard = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_card))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoadingDeck) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentDeck == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.deck_not_found),
                    style = MaterialTheme.typography.headlineMedium
                )
                Button(
                    onClick = onBackClick,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.go_back))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.card_count, cardCount),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.due_count, dueCount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (dueCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onStudyClick(deckId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = dueCount > 0
                ) {
                    Text(stringResource(R.string.start_study))
                }

                if (cards.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_cards_in_deck),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cards, key = { it.id }) { card ->
                            CardListItem(
                                card = card,
                                onClick = { cardToEdit = card },
                                onDeleteClick = { cardToDelete = card }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardListItem(
    card: CardEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.front,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = card.back,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}
