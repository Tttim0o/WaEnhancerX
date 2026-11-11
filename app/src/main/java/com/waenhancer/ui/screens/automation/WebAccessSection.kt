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
import com.waenhancer.web.WebWifi
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    // With "Per Target App" settings this screen edits one app (WhatsApp or WhatsApp Business).
    val epoch = preferenceManager.targetEpoch
    val target = preferenceManager.targetPackage
    val packages = if (target in WebAdmin.PACKAGES) listOf(target!!) else WebAdmin.PACKAGES
    var enabled by remember(epoch) { mutableStateOf(preferenceManager.getBoolean(WebServerHook.PREF_KEY, false)) }
    var portText by remember(epoch) {
        mutableStateOf(preferenceManager.getInt(WebServerHook.PREF_PORT,
            if (target == WebAdmin.BUSINESS) WebServerHook.DEFAULT_PORT + 1 else WebServerHook.DEFAULT_PORT).toString())
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
        // Also turns on the "All accounts" list inside WhatsApp, so it is shown even with the web off.
        if (target == null || target == WebAdmin.WHATSAPP) MergeAccounts(epoch)
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

        HomeWifi(epoch)

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
            val appPorts = packages.filter { WebKeepAliveService.enabled(context, it) }
                .ifEmpty { packages.take(1) }.map { pkg -> pkg to WebKeepAliveService.port(context, pkg) }
            for (ip in addresses) for ((pkg, port) in appPorts) {
                val url = "http://$ip:$port"
                val label = if (appPorts.size > 1) (if (pkg == WebAdmin.BUSINESS) "WhatsApp Business: " else "WhatsApp: ") else ""
                Text(
                    text = label + url, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = colors.primary,
                    modifier = Modifier.clickable {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("WAEX Web", url))
                        Toast.makeText(context, UiText.get(R.string.waex_ui_copy, "Copy"), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        val running = packages.filter { packages.size == 1 || WebKeepAliveService.enabled(context, it) }.ifEmpty { packages.take(1) }
        for (pkg in running) WebDevices(pkg, if (running.size > 1) pkg else null)
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
private fun WebDevices(pkg: String, heading: String?) {
    val context = LocalContext.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography
    val radius = WaexTheme.radius
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var asked by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    suspend fun refresh() {
        state = withContext(Dispatchers.IO) { WebAdmin.state(context, pkg) }
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

    if (heading != null) Text(
        text = if (heading == WebAdmin.BUSINESS) "WhatsApp Business" else "WhatsApp",
        style = typography.bodyMd, fontWeight = FontWeight.Bold, color = colors.onSurface,
        modifier = Modifier.padding(top = 8.dp)
    )
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
            TextButton(onClick = { act { WebAdmin.signOut(context, pkg, session["id"] as? String ?: "") } }) {
                Text(UiText.get(R.string.waex_ui_web_sign_out, "Sign out"), color = colors.error)
            }
        }
    }
    if (sessions.size > 1) TextButton(onClick = { act { WebAdmin.signOut(context, pkg, null) } }) {
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
                TextButton(onClick = { act { WebAdmin.deleteToken(context, pkg, token["id"] as? String ?: "") } }) {
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
                act { WebAdmin.setPassword(context, pkg, value) }
            }
        ) { Text(UiText.get(R.string.waex_ui_web_password_save, "Set password")) }
        if (passwordSet) TextButton(onClick = { act { WebAdmin.setPassword(context, pkg, null) } }) {
            Text(UiText.get(R.string.waex_ui_web_password_remove, "Remove"), color = colors.error)
        }
    }
}

/** "Only on home Wi-Fi": the page opens by itself on the listed networks and closes elsewhere. */
@Composable
private fun HomeWifi(epoch: Int) {
    val context = LocalContext.current
    val preferenceManager = LocalWaexPreferenceManager.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography
    val radius = WaexTheme.radius

    var wifiOnly by remember(epoch) { mutableStateOf(preferenceManager.getBoolean(WebServerHook.PREF_WIFI_ONLY, false)) }
    var networks by remember { mutableStateOf(WebWifi.networks(context)) }
    var current by remember { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf(WebWifi.hasLocation(context)) }
    var background by remember { mutableStateOf(WebWifi.hasBackgroundLocation(context)) }
    var typed by remember { mutableStateOf("") }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        location = WebWifi.hasLocation(context)
        background = WebWifi.hasBackgroundLocation(context)
        WebKeepAliveService.apply(context)
    }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        background = WebWifi.hasBackgroundLocation(context)
        WebKeepAliveService.apply(context)
    }

    LaunchedEffect(wifiOnly, location) {
        while (wifiOnly) {
            current = withContext(Dispatchers.IO) { WebWifi.currentName(context) }
            location = WebWifi.hasLocation(context)
            background = WebWifi.hasBackgroundLocation(context)
            delay(3000)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = UiText.get(R.string.waex_ui_web_wifi_only, "Only on home Wi-Fi"),
                style = typography.bodyMd, fontWeight = FontWeight.SemiBold, color = colors.onSurface
            )
            Text(
                text = UiText.get(R.string.waex_ui_web_wifi_only_desc,
                    "The page opens by itself on the networks below and closes everywhere else. Restart WhatsApp after switching this on."),
                style = typography.labelSm, color = colors.onSurfaceVariant
            )
        }
        Switch(checked = wifiOnly, onCheckedChange = {
            wifiOnly = it
            preferenceManager.putBoolean(WebServerHook.PREF_WIFI_ONLY, it)
            WebKeepAliveService.apply(context)
        })
    }
    if (!wifiOnly) return

    if (!location) {
        Text(
            text = UiText.get(R.string.waex_ui_web_wifi_need_location,
                "Android shows the Wi-Fi name only to apps with the location permission. WAEX uses it only for this name."),
            style = typography.labelSm, color = colors.error
        )
        TextButton(onClick = {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }) { Text(UiText.get(R.string.waex_ui_web_wifi_allow_location, "Allow location")) }
    } else if (!background) {
        Text(
            text = UiText.get(R.string.waex_ui_web_wifi_need_background,
                "To notice the Wi-Fi while WAEX is closed, choose \"Allow all the time\" for location."),
            style = typography.labelSm, color = colors.error
        )
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }) { Text(UiText.get(R.string.waex_ui_web_wifi_allow_always, "Allow all the time")) }
    }

    val now = current
    Text(
        text = when {
            now == null -> UiText.get(R.string.waex_ui_web_wifi_now_none, "Now: no Wi-Fi name (not on Wi-Fi, location off or not allowed). The page is closed.")
            now in networks -> UiText.get(R.string.waex_ui_web_wifi_now_home, "Now: %s, a home network. The page is open.", now)
            else -> UiText.get(R.string.waex_ui_web_wifi_now_other, "Now: %s, not a home network. The page is closed.", now)
        },
        style = typography.labelSm,
        color = if (now != null && now in networks) colors.primary else colors.onSurfaceVariant
    )
    if (now != null && now !in networks) {
        TextButton(onClick = {
            networks = WebWifi.add(context, now)
            WebKeepAliveService.apply(context)
        }) { Text(UiText.get(R.string.waex_ui_web_wifi_add_current, "Add %s as a home network", now)) }
    }

    Text(
        text = UiText.get(R.string.waex_ui_web_wifi_list, "Home networks"),
        style = typography.bodyMd, fontWeight = FontWeight.SemiBold, color = colors.onSurface
    )
    if (networks.isEmpty()) Text(
        text = UiText.get(R.string.waex_ui_web_wifi_empty, "No home network yet."),
        style = typography.labelSm, color = colors.onSurfaceVariant
    )
    networks.forEach { name ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = typography.bodyMd, color = colors.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                networks = WebWifi.remove(context, name)
                WebKeepAliveService.apply(context)
            }) { Text(UiText.get(R.string.waex_ui_web_delete, "Delete"), color = colors.error) }
        }
    }
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
            if (typed.isEmpty()) Text(
                text = UiText.get(R.string.waex_ui_web_wifi_name_hint, "Wi-Fi name"),
                style = typography.bodyMd, color = colors.onSurfaceVariant
            )
            BasicTextField(
                value = typed,
                onValueChange = { typed = it.take(32) },
                singleLine = true,
                textStyle = typography.bodyMd.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary)
            )
        }
        TextButton(enabled = typed.isNotBlank(), onClick = {
            networks = WebWifi.add(context, typed)
            typed = ""
            WebKeepAliveService.apply(context)
        }) { Text(UiText.get(R.string.waex_ui_web_wifi_add, "Add")) }
    }
}

/** Shows the chats of every WhatsApp account on the phone together in WAEX Web (not Business). */
@Composable
private fun MergeAccounts(epoch: Int) {
    val context = LocalContext.current
    val preferenceManager = LocalWaexPreferenceManager.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography
    var merge by remember(epoch) { mutableStateOf(preferenceManager.getBoolean(WebServerHook.PREF_MERGE, false)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = UiText.get(R.string.waex_ui_web_merge, "Merge the inboxes of all WhatsApp accounts"),
                style = typography.bodyMd, fontWeight = FontWeight.SemiBold, color = colors.onSurface
            )
            Text(
                text = UiText.get(R.string.waex_ui_web_merge_desc,
                    "For people with more than one account in WhatsApp (not Business). The web shows all chats together and lets you choose the account to send from; in WhatsApp the three-dot menu gets \"All accounts\". Accounts not in use show what they had when last in use. Restart WhatsApp after changing this."),
                style = typography.labelSm, color = colors.onSurfaceVariant
            )
        }
        Switch(checked = merge, onCheckedChange = {
            merge = it
            preferenceManager.putBoolean(WebServerHook.PREF_MERGE, it)
            // One switch for both: the web and the "All accounts" list inside WhatsApp.
            preferenceManager.putBoolean("merged_inbox", it)
            WebKeepAliveService.apply(context)
        })
    }
}
