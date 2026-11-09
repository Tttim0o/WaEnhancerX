package com.waenhancer.app.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waenhancer.app.R
import com.waenhancer.ui.designsystem.WaexTheme
import com.waenhancer.utils.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Step {
    object Idle : Step
    object Working : Step
    class PickChat(val chats: List<WhatsAppImporter.TargetChat>) : Step
    class Confirm(val chat: WhatsAppImporter.TargetChat) : Step
    class Done(val result: WhatsAppImporter.Result) : Step
    class Restored(val name: String) : Step
    class Failed(val message: String) : Step
}

/**
 * "Import into WhatsApp" for the open export. [messages] is null with [blockedReason] set when
 * this export cannot be imported. [suggestedName] preselects the chat search.
 */
@Composable
fun ImportBar(messages: List<ImportMessage>?, blockedReason: String?, suggestedName: String?) {
    val colors = WaexTheme.colors
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Step>(Step.Idle) }

    fun run(block: suspend () -> Step) {
        step = Step.Working
        scope.launch {
            step = try {
                withContext(Dispatchers.IO) { block() }
            } catch (t: Throwable) {
                Step.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            blockedReason ?: UiText.get(R.string.waex_ui_chat_import_count, "%d text messages can be imported",
                messages?.size ?: 0),
            color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            enabled = blockedReason == null && !messages.isNullOrEmpty(),
            onClick = { run { Step.PickChat(WhatsAppImporter.listChats()) } }
        ) {
            Text(UiText.get(R.string.waex_ui_chat_import, "Import into WhatsApp"))
        }
    }

    when (val s = step) {
        Step.Idle -> Unit
        Step.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(UiText.get(R.string.waex_ui_chat_import, "Import into WhatsApp")) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                    Text(UiText.get(R.string.waex_ui_chat_import_working, "Working… WhatsApp is closed meanwhile."))
                }
            }
        )
        is Step.PickChat -> ChatPicker(s.chats, suggestedName,
            onPick = { step = Step.Confirm(it) }, onDismiss = { step = Step.Idle })
        is Step.Confirm -> AlertDialog(
            onDismissRequest = { step = Step.Idle },
            title = { Text(UiText.get(R.string.waex_ui_chat_import_confirm_title, "Experimental import")) },
            text = {
                Text(UiText.get(R.string.waex_ui_chat_import_confirm,
                    "WhatsApp will be closed and its message database backed up. Then %1\$d text messages are added to %2\$s. They stay on this phone only: they are not sent, not synced to linked devices and not searchable. Messages already in the chat or newer than its last message are skipped. If anything fails, the backup is restored.",
                    messages?.size ?: 0, s.chat.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    val list = messages.orEmpty()
                    run { Step.Done(WhatsAppImporter.import(s.chat, list)) }
                }) { Text(UiText.get(R.string.waex_ui_chat_import, "Import into WhatsApp")) }
            },
            dismissButton = {
                TextButton(onClick = { step = Step.Idle }) { Text(UiText.get(R.string.action_cancel, "Cancel")) }
            }
        )
        is Step.Done -> AlertDialog(
            onDismissRequest = { step = Step.Idle },
            title = { Text(UiText.get(R.string.waex_ui_chat_import, "Import into WhatsApp")) },
            text = {
                Text(UiText.get(R.string.waex_ui_chat_import_result,
                    "Added: %1\$d\nAlready in the chat: %2\$d\nNewer than the chat's last message: %3\$d\nNo room between messages: %4\$d",
                    s.result.inserted, s.result.duplicates, s.result.newer, s.result.noRoom))
            },
            confirmButton = {
                TextButton(onClick = { step = Step.Idle }) { Text(UiText.get(R.string.action_ok, "OK")) }
            },
            dismissButton = if (s.result.backup.isNotEmpty()) {
                {
                    TextButton(onClick = {
                        val name = s.result.backup
                        run { WhatsAppImporter.restore(name); Step.Restored(name) }
                    }) { Text(UiText.get(R.string.waex_ui_chat_import_undo, "Undo (restore backup)")) }
                }
            } else null
        )
        is Step.Restored -> AlertDialog(
            onDismissRequest = { step = Step.Idle },
            text = { Text(UiText.get(R.string.waex_ui_chat_import_restored, "Backup %s restored", s.name)) },
            confirmButton = {
                TextButton(onClick = { step = Step.Idle }) { Text(UiText.get(R.string.action_ok, "OK")) }
            }
        )
        is Step.Failed -> AlertDialog(
            onDismissRequest = { step = Step.Idle },
            title = { Text(UiText.get(R.string.waex_ui_chat_import_failed, "Import failed")) },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { step = Step.Idle }) { Text(UiText.get(R.string.action_ok, "OK")) }
            }
        )
    }
}

@Composable
private fun ChatPicker(
    chats: List<WhatsAppImporter.TargetChat>,
    suggestedName: String?,
    onPick: (WhatsAppImporter.TargetChat) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = WaexTheme.colors
    var query by remember { mutableStateOf(suggestedName.orEmpty()) }
    val shown = remember(query, chats) {
        val q = query.trim()
        if (q.isEmpty()) chats else chats.filter { it.name.contains(q, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiText.get(R.string.waex_ui_chat_import_pick, "Add the messages to which chat?")) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.rowId }) { chat ->
                        Text(
                            chat.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(chat) }
                                .padding(vertical = 12.dp)
                        )
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(UiText.get(R.string.action_cancel, "Cancel")) }
        }
    )
}

/** Why an export cannot be imported, or null when it can. */
fun importBlockReason(isGroup: Boolean, needsMe: Boolean, count: Int?): String? = when {
    isGroup -> UiText.get(R.string.waex_ui_chat_import_groups, "Import works for one-to-one chats only")
    needsMe -> UiText.get(R.string.waex_ui_chat_import_need_me, "Choose your name above to import")
    count == null || count == 0 -> UiText.get(R.string.waex_ui_chat_import_none,
        "Nothing to import. Exports made before this version need to be exported again.")
    else -> null
}
