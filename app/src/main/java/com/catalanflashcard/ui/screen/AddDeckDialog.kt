package com.catalanflashcard.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catalanflashcard.R
import com.catalanflashcard.ui.viewmodel.DeckViewModel

@Composable
fun AddDeckDialog(
    viewModel: DeckViewModel,
    onDismiss: () -> Unit
) {
    var deckName by remember { mutableStateOf("") }
    var deckDescription by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_deck_title)) },
        text = {
            Column {
                TextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    label = { Text(stringResource(R.string.deck_name_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = deckDescription,
                    onValueChange = { deckDescription = it },
                    label = { Text(stringResource(R.string.deck_description_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (deckName.isNotBlank()) {
                        viewModel.createDeck(deckName, deckDescription)
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
