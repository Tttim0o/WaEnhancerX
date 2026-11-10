package com.waenhancer.ui.screens.automation

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waenhancer.app.R
import com.waenhancer.ui.designsystem.WaexTheme
import com.waenhancer.ui.navigation.LocalWaexPreferenceManager
import com.waenhancer.utils.UiText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.waenhancer.web.LanPolicy
import com.waenhancer.web.WebAdmin
import com.waenhancer.web.WebKeepAliveService
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import com.waenhancer.xposed.features.web.WebServerHook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Settings for WAEX Web: the switch, the port and the address to open on a computer.
 * Passwords, signed-in browsers and API tokens are managed on the web page itself.
 */
@Composable
fun WebAccessSection() {
    val context = LocalContext.current
    val preferenceManager = LocalWaexPreferenceManager.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography
    val radius = WaexTheme.radius

    var enabled by remember { mutableStateOf(preferenceManager.getBoolean(WebServerHook.PREF_KEY, false)) }
    var portText by remember {
        mutableStateOf(preferenceManager.getInt(WebServerHook.PREF_PORT, WebServerHook.DEFAULT_PORT).toString())
    }
    var addresses by remember { mutableStateOf(emptyList<String>()) }

    // The Wi-Fi address can change; look it up again every few seconds while the screen is open.
    LaunchedEffect(enabled) {
        while (enabled) {
            addresses = withContext(Dispatchers.IO) {
                LanPolicy.localInterfaces().map { LanPolicy.format(it.address) }.distinct()
            }
            delay(5000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = UiText.get(R.string.waex_ui_web_title, "WAEX Web: chats in a browser (experimental)"),
                style = typography.bodyMd, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                preferenceManager.putBoolean(WebServerHook.PREF_KEY, it)
                WebKeepAliveService.apply(context)
            })
        }
        Text(
            text = UiText.get(R.string.waex_ui_web_desc,
                "Read and write your chats from a computer on the same Wi-Fi. A new browser needs a code that this phone shows in a notification. You can add a password on the web page."),
            style = typography.labelSm, color = colors.onSurfaceVariant
        )
        if (!enabled) return@Column

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = UiText.get(R.string.waex_ui_port, "Port"), style = typography.bodyMd, color = colors.onSurface)
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(110.dp)
                    .height(40.dp)
                    .clip(radius.mdShape)
                    .background(colors.surface)
                    .border(1.dp, colors.outlineVariant, radius.mdShape)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = portText,
                    onValueChange = { input ->
                        portText = input.filter(Char::isDigit).take(5)
                        val port = portText.toIntOrNull()
                        if (port != null && port in 1024..65535) {
                            preferenceManager.putInt(WebServerHook.PREF_PORT, port)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = typography.bodyMd.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary)
                )
            }
        }

        val port = portText.toIntOrNull()?.takeIf { it in 1024..65535 } ?: WebServerHook.DEFAULT_PORT
        if (addresses.isEmpty()) {
            Text(
                text = UiText.get(R.string.waex_ui_web_no_wifi, "Connect the phone to Wi-Fi to see the address."),
                style = typography.labelSm, color = colors.onSurfaceVariant
            )
        } else {
            Text(
                text = UiText.get(R.string.waex_ui_web_address, "Open this address on your computer"),
                style = typography.labelSm, color = colors.onSurfaceVariant
            )
            addresses.forEach { ip ->
                val url = "http://$ip:$port"
                Text(
                    text = url, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = colors.primary,
                    modifier = Modifier.clickable {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("WAEX Web", url))
                        Toast.makeText(context, UiText.get(R.string.waex_ui_copy, "Copy"), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        WebDevices()
        Text(
            text = UiText.get(R.string.waex_ui_web_restart,
                "Restart WhatsApp after changing this. WhatsApp must be running for the page to work."),
            style = typography.labelSm, color = colors.onSurfaceVariant
        )
        Text(
            text = UiText.get(R.string.waex_ui_web_plain_http,
                "The connection is not encrypted. Use it only on a Wi-Fi you trust. Locked chats are never shown."),
            style = typography.labelSm, color = colors.error
        )
    }
}

/** Who is signed in to WAEX Web, the code a new browser is waiting for, and the password. */
@Suppress("UNCHECKED_CAST")
@Composable
private fun WebDevices() {
    val context = LocalContext.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography
    val radius = WaexTheme.radius
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var asked by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    suspend fun refresh() {
        state = withContext(Dispatchers.IO) { WebAdmin.state(context) }
        asked = true
    }
    fun act(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            refresh()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(2000)
        }
    }

    val current = state
    if (current == null) {
        if (asked) Text(
            text = UiText.get(R.string.waex_ui_web_not_running,
                "The page is not running yet. Open WhatsApp once (restart it after switching this on)."),
            style = typography.labelSm, color = colors.error
        )
        return
    }

    Text(
        text = UiText.get(R.string.waex_ui_web_is_running, "Running. WAEX keeps WhatsApp awake while this is on."),
        style = typography.labelSm, color = colors.primary
    )
    (current["code"] as? String)?.let { code ->
        Text(
            text = UiText.get(R.string.waex_ui_web_code_now, "Code for the browser that is signing in now"),
            style = typography.labelSm, color = colors.onSurfaceVariant
        )
        Text(
            text = code.take(3) + " " + code.drop(3),
            fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.onSurface
        )
    }
    if (current["canSend"] != true) Text(
        text = UiText.get(R.string.waex_ui_web_cannot_send,
            "Sending is not available on this WhatsApp version. Reading works."),
        style = typography.labelSm, color = colors.error
    )
    (current["lastError"] as? String)?.takeIf { it.isNotEmpty() }?.let {
        Text(
            text = UiText.get(R.string.waex_ui_web_last_error, "Last sending problem: %s", it),
            style = typography.labelSm, color = colors.error
        )
    }

    Text(
        text = UiText.get(R.string.waex_ui_web_devices, "Signed-in browsers"),
        style = typography.bodyMd, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
        modifier = Modifier.padding(top = 8.dp)
    )
    val sessions = (current["sessions"] as? List<Map<String, Any?>>).orEmpty()
    if (sessions.isEmpty()) Text(
        text = UiText.get(R.string.waex_ui_web_no_devices, "No browser is signed in."),
        style = typography.labelSm, color = colors.onSurfaceVariant
    )
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    sessions.forEach { session ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session["label"] as? String ?: "Browser", style = typography.bodyMd, color = colors.onSurface)
                Text(
                    text = UiText.get(R.string.waex_ui_web_last_used, "Last used %s",
                        format.format(Date(((session["lastSeen"] as? Double) ?: 0.0).toLong()))),
                    style = typography.labelSm, color = colors.onSurfaceVariant
                )
            }
            TextButton(onClick = { act { WebAdmin.signOut(context, session["id"] as? String ?: "") } }) {
                Text(UiText.get(R.string.waex_ui_web_sign_out, "Sign out"), color = colors.error)
            }
        }
    }
    if (sessions.size > 1) TextButton(onClick = { act { WebAdmin.signOut(context, null) } }) {
        Text(UiText.get(R.string.waex_ui_web_sign_out_all, "Sign out all browsers"), color = colors.error)
    }

    val tokens = (current["tokens"] as? List<Map<String, Any?>>).orEmpty()
    if (tokens.isNotEmpty()) {
        Text(
            text = UiText.get(R.string.waex_ui_web_tokens, "API tokens"),
            style = typography.bodyMd, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        tokens.forEach { token ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = token["name"] as? String ?: "API", style = typography.bodyMd, color = colors.onSurface,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { act { WebAdmin.deleteToken(context, token["id"] as? String ?: "") } }) {
                    Text(UiText.get(R.string.waex_ui_web_delete, "Delete"), color = colors.error)
                }
            }
        }
    }

    val passwordSet = current["passwordSet"] == true
    Text(
        text = if (passwordSet) UiText.get(R.string.waex_ui_web_password_on, "Password: set. A new browser needs the code and the password.")
        else UiText.get(R.string.waex_ui_web_password_off, "Password: none. A new browser needs only the code."),
        style = typography.bodyMd, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(radius.mdShape)
                .background(colors.surface)
                .border(1.dp, colors.outlineVariant, radius.mdShape)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = typography.bodyMd.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary)
            )
        }
        TextButton(
            enabled = password.length >= 6,
            onClick = {
                val value = password
                password = ""
                act { WebAdmin.setPassword(context, value) }
            }
        ) { Text(UiText.get(R.string.waex_ui_web_password_save, "Set password")) }
        if (passwordSet) TextButton(onClick = { act { WebAdmin.setPassword(context, null) } }) {
            Text(UiText.get(R.string.waex_ui_web_password_remove, "Remove"), color = colors.error)
        }
    }
}
