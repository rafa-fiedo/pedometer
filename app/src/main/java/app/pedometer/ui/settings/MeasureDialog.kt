package app.pedometer.ui.settings

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.pedometer.R
import app.pedometer.core.BodySize

/**
 * Edits one whole-number measurement. An empty field saves null (clears the value); anything
 * else must be a whole number inside [range], or [error] is shown.
 */
@Composable
fun MeasureDialog(
    title: String,
    label: String,
    error: String,
    initial: Int?,
    range: IntRange,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial?.toString() ?: "") }
    var showError by rememberSaveable { mutableStateOf(false) }

    val save = {
        if (text.isBlank()) {
            onSave(null)
        } else {
            val value = BodySize.parseMeasure(text, range)
            if (value == null) showError = true else onSave(value)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    showError = false
                },
                label = { Text(label) },
                isError = showError,
                supportingText = if (showError) {
                    { Text(error) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = {
            TextButton(onClick = save) { Text(stringResource(R.string.goal_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.goal_cancel)) }
        },
    )
}
