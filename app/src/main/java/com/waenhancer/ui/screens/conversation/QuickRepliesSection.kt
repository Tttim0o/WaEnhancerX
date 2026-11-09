package com.waenhancer.ui.screens.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waenhancer.app.R
import com.waenhancer.config.QuickReplies
import com.waenhancer.ui.components.WaexCard
import com.waenhancer.ui.components.WaexSectionHeader
import com.waenhancer.ui.components.WaexSwitchPreference
import com.waenhancer.ui.designsystem.WaexIcons
import com.waenhancer.ui.designsystem.WaexTheme
import com.waenhancer.ui.navigation.LocalWaexPreferenceManager
import com.waenhancer.utils.UiText

/** Saved replies offered in WhatsApp's message field after typing "/". */
@Composable
fun QuickRepliesSection(targetEpoch: Int = 0) {
    val prefs = LocalWaexPreferenceManager.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography

    var enabled by remember(targetEpoch) {
        mutableStateOf(prefs.getBoolean(QuickReplies.ENABLED_KEY, true))
    }
    var items by remember(targetEpoch) {
        mutableStateOf(QuickReplies.decode(prefs.getString(QuickReplies.PREF_KEY, "")))
    }
    // Index being edited; -1 adds a new reply, null closes the dialog.
    var editing by remember { mutableStateOf<Int?>(null) }

    fun save(updated: List<QuickReplies.Item>) {
        items = updated
        prefs.putString(QuickReplies.PREF_KEY, QuickReplies.encode(updated))
    }

    WaexSectionHeader(
        title = UiText.get(R.string.waex_ui_quick_replies, "Quick replies"),
        subtitle = UiText.get(R.string.waex_ui_quick_replies_desc,
            "Type / in a chat to insert a saved reply")
    )
    WaexCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            WaexSwitchPreference(
                title = UiText.get(R.string.waex_ui_quick_replies, "Quick replies"),
                description = UiText.get(R.string.waex_ui_quick_replies_hint,
                    "Restart WhatsApp after turning this on"),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    prefs.putBoolean(QuickReplies.ENABLED_KEY, it)
                },
                icon = WaexIcons.AutoAwesome,
                showDivider = true
            )
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = index }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "/" + item.shortcut,
                            style = typography.bodyMd,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                        Text(
                            text = item.text,
                            style = typography.bodySm,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { save(items.filterIndexed { i, _ -> i != index }) }) {
                        Icon(WaexIcons.Clear,
                            contentDescription = UiText.get(R.string.waex_ui_clear, "Clear"),
                            tint = colors.error)
                    }
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
            }
            TextButton(
                onClick = { editing = -1 },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(UiText.get(R.string.waex_ui_quick_replies_add, "Add reply"))
            }
        }
    }

    val index = editing
    if (index != null) {
        val original = items.getOrNull(index)
        var shortcut by remember(index) { mutableStateOf(original?.shortcut.orEmpty()) }
        var text by remember(index) { mutableStateOf(original?.text.orEmpty()) }
        val normalized = QuickReplies.normalizeShortcut(shortcut)
        val duplicate = items.withIndex().any { it.index != index && it.value.shortcut == normalized }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(UiText.get(R.string.waex_ui_quick_replies, "Quick replies")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = { Text(UiText.get(R.string.waex_ui_quick_replies_shortcut, "Shortcut")) },
                        prefix = { Text("/") },
                        singleLine = true,
                        isError = duplicate,
                        supportingText = if (duplicate) {
                            { Text(UiText.get(R.string.waex_ui_quick_replies_duplicate, "This shortcut already exists")) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(UiText.get(R.string.waex_ui_quick_replies_text, "Reply text")) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalized.isNotEmpty() && text.isNotBlank() && !duplicate,
                    onClick = {
                        val item = QuickReplies.Item(normalized, text)
                        save(if (index in items.indices) {
                            items.mapIndexed { i, old -> if (i == index) item else old }
                        } else {
                            items + item
                        })
                        editing = null
                    }
                ) { Text(UiText.get(R.string.waex_ui_quick_replies_save, "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(UiText.get(R.string.action_cancel, "Cancel"))
                }
            }
        )
    }
}
