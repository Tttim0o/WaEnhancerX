package com.waenhancer.ui.screens.automation

import com.waenhancer.app.R
import com.waenhancer.utils.UiText

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.waenhancer.core.transcription.server.LocalApiServer
import com.waenhancer.core.transcription.server.TranscriptionServerService
import com.waenhancer.ui.designsystem.WaexTheme
import com.waenhancer.ui.navigation.LocalWaexPreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings for the local OpenAI-compatible transcription API: off / this device / local
 * network, port, keep-running, API key, and a root battery exemption. Experimental.
 */
@Composable
fun TranscriptionServerSection() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val preferenceManager = LocalWaexPreferenceManager.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography
    val radius = WaexTheme.radius
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(TranscriptionServerService.mode(appContext)) }
    var portText by remember { mutableStateOf(TranscriptionServerService.port(appContext).toString()) }
    var always by remember { mutableStateOf(preferenceManager.getBoolean(TranscriptionServerService.PREF_ALWAYS, false)) }
    var key by remember { mutableStateOf("") }
    var rootResult by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { key = withContext(Dispatchers.IO) { TranscriptionServerService.key(appContext) } }
    // Server state changes in the background service; refresh the status line once a second.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    fun apply() = TranscriptionServerService.apply(appContext)

    fun copy(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = UiText.get(R.string.waex_ui_share_on_device_models_with_other_apps, "Share on-device models with other apps (experimental)"),
            style = typography.bodyMd, fontWeight = FontWeight.SemiBold, color = colors.onSurface
        )
        Text(
            text = UiText.get(R.string.waex_ui_an_openai_compatible_api_v1_audio_tran,
                "An OpenAI-compatible API (/v1/audio/transcriptions) that other apps can use with your downloaded models."),
            style = typography.labelSm, color = colors.onSurfaceVariant
        )

        listOf(
            LocalApiServer.MODE_OFF to UiText.get(R.string.waex_ui_off, "Off"),
            LocalApiServer.MODE_DEVICE to UiText.get(R.string.waex_ui_this_device_only, "This device only"),
            LocalApiServer.MODE_NETWORK to UiText.get(R.string.waex_ui_local_network, "Local network")
        ).forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    mode = value
                    preferenceManager.putString(TranscriptionServerService.PREF_MODE, value)
                    apply()
                }
            ) {
                RadioButton(selected = mode == value, onClick = {
                    mode = value
                    preferenceManager.putString(TranscriptionServerService.PREF_MODE, value)
                    apply()
                })
                Text(text = label, style = typography.bodyMd, color = colors.onSurface)
            }
        }
        if (mode == LocalApiServer.MODE_OFF) return@Column

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
                            preferenceManager.putInt(TranscriptionServerService.PREF_PORT, port)
                            apply()
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = typography.bodyMd.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = UiText.get(R.string.waex_ui_keep_running_when_waex_doesn_t_use_on, "Keep running when WAEX doesn't use on-device models"),
                style = typography.bodyMd, color = colors.onSurface, modifier = Modifier.weight(1f)
            )
            Switch(checked = always, onCheckedChange = {
                always = it
                preferenceManager.putBoolean(TranscriptionServerService.PREF_ALWAYS, it)
                apply()
            })
        }

        @Suppress("UNUSED_VARIABLE") val refresh = tick
        val running = LocalApiServer.isRunning()
        val urls = TranscriptionServerService.baseUrls(LocalApiServer.runningMode(), LocalApiServer.runningPort())
        Text(
            text = when {
                running -> UiText.get(R.string.waex_ui_running, "Running") + ":\n" + urls.joinToString("\n")
                !TranscriptionServerService.shouldRun(appContext) ->
                    UiText.get(R.string.waex_ui_not_running_waex_isn_t_using_on_device, "Not running: WAEX isn't using on-device models.")
                else -> UiText.get(R.string.waex_ui_stopped, "Stopped") +
                        (LocalApiServer.lastError()?.let { ": $it" } ?: "")
            },
            style = typography.labelSm,
            color = if (running) colors.primary else colors.onSurfaceVariant
        )
        if (mode == LocalApiServer.MODE_NETWORK) {
            Text(
                text = UiText.get(R.string.waex_ui_anyone_on_your_network_with_the_key_ca, "Anyone on your network with the key can use it."),
                style = typography.labelSm, color = colors.error
            )
        }

        Text(text = UiText.get(R.string.waex_ui_api_key, "API key"), style = typography.labelSm, color = colors.onSurfaceVariant)
        Text(text = key, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colors.onSurface)
        Row {
            TextButton(onClick = { copy("API key", key) }, enabled = key.isNotEmpty()) {
                Text(UiText.get(R.string.waex_ui_copy, "Copy"))
            }
            TextButton(onClick = {
                scope.launch {
                    key = withContext(Dispatchers.IO) { TranscriptionServerService.newKey(appContext) }
                    apply() // Restart so the old key stops working.
                }
            }) { Text(UiText.get(R.string.waex_ui_new_key, "New key")) }
        }

        val example = "curl " + (urls.lastOrNull() ?: "http://127.0.0.1:${portText}/v1") +
                "/audio/transcriptions -H \"Authorization: Bearer $key\" -F file=@note.ogg"
        Text(text = UiText.get(R.string.waex_ui_example, "Example"), style = typography.labelSm, color = colors.onSurfaceVariant)
        Text(
            text = example, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = colors.onSurface,
            modifier = Modifier.clickable { copy("Example", example) }
        )

        TextButton(onClick = {
            scope.launch {
                rootResult = withContext(Dispatchers.IO) { TranscriptionServerService.exemptFromBatteryOptimization(appContext) }
            }
        }) { Text(UiText.get(R.string.waex_ui_keep_alive_with_root, "Keep alive with root")) }
        Text(
            text = if (rootResult.isNotEmpty()) rootResult
            else UiText.get(R.string.waex_ui_exempts_waex_from_battery_optimization, "Exempts WAEX from battery optimization so Android doesn't stop the server."),
            style = typography.labelSm, color = colors.onSurfaceVariant
        )
    }
}
