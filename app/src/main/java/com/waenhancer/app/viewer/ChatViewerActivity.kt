package com.waenhancer.app.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.waenhancer.app.R
import com.waenhancer.ui.designsystem.WaexTheme
import com.waenhancer.utils.LocaleHelper
import com.waenhancer.utils.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Shows a chat exported by WAEX or by WhatsApp's own "Export chat" (TXT, or the ZIP WhatsApp
 * shares). Only reads the chosen file; nothing is written to WhatsApp.
 */
class ChatViewerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initial = intent?.data ?: if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } else null
        setContent {
            WaexTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = WaexTheme.colors.background) {
                    ChatViewerScreen(initial)
                }
            }
        }
    }
}

private sealed interface Loaded {
    class Text(val name: String, val chat: ChatTranscriptParser.Chat) : Loaded
    class Html(val name: String, val html: String) : Loaded
    class Failed(val message: String) : Loaded
}

/** Large files are cut here so a huge export cannot run the app out of memory. */
private const val MAX_CHARS = 8_000_000

private fun displayName(context: Context, uri: Uri): String = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Throwable) {
    null
} ?: uri.lastPathSegment ?: "chat"

private fun readText(stream: java.io.InputStream): String {
    val reader = stream.bufferedReader(Charsets.UTF_8)
    val buffer = CharArray(64 * 1024)
    val out = StringBuilder()
    while (out.length < MAX_CHARS) {
        val n = reader.read(buffer)
        if (n < 0) break
        out.append(buffer, 0, n)
    }
    return out.toString()
}

private fun load(context: Context, uri: Uri): Loaded {
    val name = displayName(context, uri)
    val lower = name.lowercase(Locale.ROOT)
    val type = context.contentResolver.getType(uri).orEmpty()
    val input = context.contentResolver.openInputStream(uri) ?: error("cannot open")
    input.use { stream ->
        if (lower.endsWith(".zip") || type.contains("zip")) {
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name.lowercase(Locale.ROOT)
                    if (entry.isDirectory) continue
                    if (entryName.endsWith(".txt")) {
                        return Loaded.Text(name, ChatTranscriptParser.parse(readText(zip)))
                    }
                    if (entryName.endsWith(".html") || entryName.endsWith(".htm")) {
                        return Loaded.Html(name, readText(zip))
                    }
                }
            }
            error("no chat in zip")
        }
        val text = readText(stream)
        val looksHtml = lower.endsWith(".html") || lower.endsWith(".htm") || type.contains("html")
            || text.trimStart().startsWith("<!doctype html", ignoreCase = true)
        return if (looksHtml) Loaded.Html(name, text) else Loaded.Text(name, ChatTranscriptParser.parse(text))
    }
}

@Composable
private fun ChatViewerScreen(initial: Uri?) {
    val context = LocalContext.current
    val colors = WaexTheme.colors
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var busy by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf<String?>(null) }

    fun open(uri: Uri) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    load(context, uri)
                } catch (t: Throwable) {
                    Loaded.Failed(UiText.get(R.string.waex_ui_chat_viewer_error, "Could not read this file"))
                }
            }
            if (result is Loaded.Text) {
                val you = UiText.get(R.string.waex_ui_chat_export_you, "You")
                val senders = result.chat.senders
                me = senders.firstOrNull { it == you || it == "You" }
            }
            loaded = result
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) open(uri)
    }
    val pick = { picker.launch(arrayOf("text/plain", "text/html", "application/zip", "application/octet-stream")) }

    LaunchedEffect(initial) {
        if (initial != null) open(initial)
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF008069))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = when (val l = loaded) {
                is Loaded.Text -> l.chat.title ?: l.name.removeSuffix(".txt").removeSuffix(".zip")
                is Loaded.Html -> l.name
                else -> UiText.get(R.string.waex_ui_chat_viewer, "Exported chats")
            }
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = pick) {
                Text(UiText.get(R.string.waex_ui_chat_viewer_open, "Open file"), color = Color.White)
            }
        }

        when (val l = loaded) {
            null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (busy) "…" else UiText.get(R.string.waex_ui_chat_viewer_desc,
                            "Open a chat exported by WAEX or by WhatsApp (TXT, ZIP or HTML). Nothing is written to WhatsApp."),
                        color = colors.onSurfaceVariant
                    )
                    if (!busy) Button(onClick = pick) {
                        Text(UiText.get(R.string.waex_ui_chat_viewer_open, "Open file"))
                    }
                }
            }
            is Loaded.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(l.message, color = colors.error)
            }
            is Loaded.Html -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                    }
                },
                update = { it.loadDataWithBaseURL(null, l.html, "text/html", "utf-8", null) }
            )
            is Loaded.Text -> TranscriptView(l.chat, me) { me = it }
        }
    }
}

@Composable
private fun TranscriptView(chat: ChatTranscriptParser.Chat, me: String?, onMe: (String?) -> Unit) {
    val colors = WaexTheme.colors
    if (chat.messages.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(UiText.get(R.string.waex_ui_chat_viewer_empty, "No messages found in this file"),
                color = colors.onSurfaceVariant)
        }
        return
    }
    val senders = remember(chat) { chat.senders }
    val group = senders.size > 2
    val bubbleIn = if (colors.isLight) Color.White else Color(0xFF202C33)
    val bubbleOut = if (colors.isLight) Color(0xFFD9FDD3) else Color(0xFF005C4B)
    val accent = Color(0xFF06CF9C)

    Column(Modifier.fillMaxSize()) {
        if (senders.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(UiText.get(R.string.waex_ui_chat_viewer_me, "Your name in this chat:"),
                    color = colors.onSurfaceVariant, fontSize = 13.sp)
                senders.take(30).forEach { s ->
                    FilterChip(selected = s == me, onClick = { onMe(if (s == me) null else s) },
                        label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            itemsIndexed(chat.messages) { index, m ->
                val previous = chat.messages.getOrNull(index - 1)
                Column(Modifier.fillMaxWidth()) {
                    if (previous == null || previous.day != m.day) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(m.day, fontSize = 12.sp, color = colors.onSurfaceVariant,
                                modifier = Modifier
                                    .background(bubbleIn, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    if (m.sender == null) {
                        Text(m.text, fontSize = 12.sp, color = colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    } else {
                    val mine = m.sender == me
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .background(if (mine) bubbleOut else bubbleIn, RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            val showName = !mine && (group || me == null) && previous?.sender != m.sender
                            if (showName) {
                                Text(m.sender.orEmpty(), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(m.text, color = colors.onSurface, fontSize = 15.sp)
                            Text(m.time, color = colors.onSurfaceVariant, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.End))
                        }
                    }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
