package com.catalanflashcard.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catalanflashcard.R
import com.catalanflashcard.ui.viewmodel.DeckViewModel

@Composable
fun DeckDetailScreen(
    deckId: Long,
    deckViewModel: DeckViewModel,
    onBackClick: () -> Unit,
    onStudyClick: (Long) -> Unit
) {
    val decks by deckViewModel.decks.collectAsState()
    val cardCount by deckViewModel.selectedDeckCardCount.collectAsState()
    val dueCount by deckViewModel.selectedDeckDueCount.collectAsState()
    val currentDeck = decks.find { it.id == deckId }

    LaunchedEffect(deckId) {
        deckViewModel.loadDeckStats(deckId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDeck?.name ?: stringResource(R.string.deck_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (currentDeck == null) {
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
                if (currentDeck.description.isNotEmpty()) {
                    Text(
                        text = currentDeck.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    enabled = dueCount > 0
                ) {
                    Text(stringResource(R.string.start_study))
                }
            }
        }
    }
}
