package com.shanqijie.fitnessapp.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onContinueEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onContinueEditing,
        title = { Text("保存未完成的修改？") },
        text = { Text("你可以保存后离开、放弃修改，或继续编辑。") },
        confirmButton = {
            Button(onClick = onSave, modifier = Modifier.testTag("dirty-back-save")) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("dirty-back-discard")) { Text("放弃") }
            TextButton(onClick = onContinueEditing, modifier = Modifier.testTag("dirty-back-continue")) { Text("继续编辑") }
        },
        modifier = Modifier.testTag("dirty-back-dialog"),
    )
}
