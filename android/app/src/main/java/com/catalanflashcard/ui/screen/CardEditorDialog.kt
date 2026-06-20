package com.catalanflashcard.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * Add/edit dialog for a single card. Used for both creating a new card
 * (isEdit = false) and editing an existing one (isEdit = true, with initial
 * values pre-filled). Confirm is enabled only when both fields are non-blank.
 */
@Composable
fun CardEditorDialog(
    isEdit: Boolean,
    onConfirm: (front: String, back: String) -> Unit,
    onDismiss: () -> Unit,
    initialFront: String = "",
    initialBack: String = ""
) {
    // Key on the initial values so the fields re-initialise if the dialog is ever
    // reused for a different card without leaving composition (e.g. a future
    // prev/next editor); today it's modal and always recomposes fresh.
    var front by remember(initialFront) { mutableStateOf(initialFront) }
    var back by remember(initialBack) { mutableStateOf(initialBack) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEdit) R.string.edit_card_title else R.string.add_card_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text(stringResource(R.string.front)) },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text(stringResource(R.string.back)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = front.isNotBlank() && back.isNotBlank(),
                onClick = {
                    onConfirm(front, back)
                    onDismiss()
                }
            ) {
                Text(stringResource(if (isEdit) R.string.save else R.string.create))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
