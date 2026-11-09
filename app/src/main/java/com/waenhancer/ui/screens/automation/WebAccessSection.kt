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
import com.waenhancer.web.LanPolicy
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
