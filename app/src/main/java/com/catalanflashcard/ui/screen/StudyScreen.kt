package com.catalanflashcard.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catalanflashcard.R
import com.catalanflashcard.ui.theme.Green
import com.catalanflashcard.ui.theme.Orange
import com.catalanflashcard.ui.theme.Red
import com.catalanflashcard.ui.viewmodel.StudyViewModel

@Composable
fun StudyScreen(
    deckId: Long,
    viewModel: StudyViewModel,
    onBackClick: () -> Unit
) {
    val currentCard by viewModel.currentCard.collectAsState()
    val dueCards by viewModel.dueCards.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(deckId) {
        viewModel.loadDueCards(deckId)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (currentCard == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_cards),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) {
                        Text(stringResource(R.string.back_to_decks))
                    }
                }
            } else {
                val card = checkNotNull(currentCard)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.card_progress, currentIndex + 1, dueCards.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FlashCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clickable { viewModel.flipCard() }
                                .animateContentSize(),
                            label = if (isFlipped) stringResource(R.string.back) else stringResource(R.string.front),
                            text = if (isFlipped) card.back else card.front
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.tap_to_flip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewButton(
                            label = stringResource(R.string.again),
                            color = Red,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.answerCard(1) }
                        )
                        ReviewButton(
                            label = stringResource(R.string.hard),
                            color = Orange,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.answerCard(3) }
                        )
                        ReviewButton(
                            label = stringResource(R.string.good),
                            color = Green,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.answerCard(4) }
                        )
                        ReviewButton(
                            label = stringResource(R.string.easy),
                            color = Color(0xFF1976D2),
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.answerCard(5) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlashCard(
    modifier: Modifier = Modifier,
    label: String,
    text: String
) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ReviewButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, color = Color.White)
    }
}
