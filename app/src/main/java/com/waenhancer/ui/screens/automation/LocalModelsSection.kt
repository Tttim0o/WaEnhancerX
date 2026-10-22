package com.waenhancer.ui.screens.automation

import com.waenhancer.app.R
import com.waenhancer.utils.UiText

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waenhancer.ui.navigation.LocalWaexPreferenceManager
import com.waenhancer.core.transcription.local.LocalModelStore
import com.waenhancer.core.transcription.local.LocalModels
import com.waenhancer.core.transcription.local.WhisperNative
import com.waenhancer.ui.designsystem.WaexTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-device whisper models: what this phone can run, the recommended model, and download /
 * use / delete for each. Downloads continue in the background if the screen is left.
 */
@Composable
fun LocalModelsSection(language: String) {
    val context = LocalContext.current
    val preferenceManager = LocalWaexPreferenceManager.current
    val colors = WaexTheme.colors
    val typography = WaexTheme.typography
    val radius = WaexTheme.radius
    val scope = rememberCoroutineScope()

    var supported by remember { mutableStateOf<Boolean?>(null) }
    var selected by remember { mutableStateOf(preferenceManager.getString("local_model", "")) }
    var tick by remember { mutableIntStateOf(0) }
    val totalRam = remember { LocalModelStore.totalRam(context) }
    val cores = remember { Runtime.getRuntime().availableProcessors() }
    val recommendation = remember(language) { LocalModels.recommend(totalRam, cores, language) }

    // Checking CPU support loads the native library: do it off the UI thread.
    LaunchedEffect(Unit) {
        supported = withContext(Dispatchers.IO) { WhisperNative.isSupported() }
    }
    // Download progress lives outside Compose; refresh twice a second while visible.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = UiText.get(R.string.waex_ui_on_device_models_2c7e25e0, "On-device models"),
            style = typography.labelSm,
            color = colors.onSurfaceVariant
        )
        Text(
            text = UiText.get(R.string.waex_ui_runs_on_your_phone_nothing_is_uploaded_086e584c,
                "Runs on your phone, nothing is uploaded. Larger models are more accurate but slower."),
            style = typography.labelSm,
            color = colors.onSurfaceVariant
        )
        if (supported == false) {
            Text(
                text = UiText.get(R.string.waex_ui_on_device_transcription_isn_t_availabl_678d16ae,
                    "On-device transcription isn't available on this phone") +
                        ": " + (WhisperNative.unsupportedReason() ?: ""),
                style = typography.bodyMd,
                color = colors.error
            )
            return@Column
        }
        Text(
            text = UiText.get(R.string.waex_ui_this_phone_154bf57e, "This phone") + ": " +
                    recommendation.reason.substringBefore(':') + " · " + cores + " cores",
            style = typography.bodyMd,
            color = colors.onSurface
        )
        Text(
            text = UiText.get(R.string.waex_ui_recommended_d70604e8, "Recommended") + ": " +
                    recommendation.model.label + " (" + recommendation.model.sizeLabel() + ") · " +
                    recommendation.reason.substringAfter(": "),
            style = typography.bodyMd,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary
        )
        if (LocalModels.byId(selected)?.let { LocalModelStore.isDownloaded(context, it) } != true) {
            Text(
                text = UiText.get(R.string.waex_ui_choose_a_downloaded_model_to_use_3e3f82a7,
                    "Choose a downloaded model to use."),
                style = typography.labelSm,
                color = colors.onSurfaceVariant
            )
        }

        LocalModels.ALL.forEach { model ->
            // Read tick so these re-evaluate as downloads progress.
            @Suppress("UNUSED_VARIABLE") val refresh = tick
            val downloaded = LocalModelStore.isDownloaded(context, model)
            val progress = LocalModelStore.progress(model.id)
            val isSelected = selected == model.id && downloaded
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isSelected) colors.primary else colors.outlineVariant, radius.mdShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = model.label + " · " + model.sizeLabel(), style = typography.bodyMd,
                            fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        val tags = buildList {
                            if (model.id == recommendation.model.id) {
                                add(UiText.get(R.string.waex_ui_recommended_d70604e8, "Recommended"))
                            }
                            if (model.englishOnly) add(UiText.get(R.string.waex_ui_english_only_eda32f28, "English only"))
                            if (isSelected) add(UiText.get(R.string.waex_ui_in_use_750af77a, "In use"))
                        }
                        if (tags.isNotEmpty()) {
                            Text(text = tags.joinToString(" · "), style = typography.labelSm, color = colors.primary)
                        }
                        progress?.error?.let {
                            Text(text = it, style = typography.labelSm, color = colors.error)
                        }
                    }
                    when {
                        progress?.running == true -> TextButton(onClick = { LocalModelStore.cancel(model.id) }) {
                            Text(UiText.get(R.string.waex_ui_cancel_19766ed6, "Cancel"))
                        }
                        !downloaded -> TextButton(onClick = { LocalModelStore.download(context, model) }) {
                            Text(UiText.get(R.string.waex_ui_download_d6eafe82, "Download"))
                        }
                        else -> Row {
                            if (!isSelected) {
                                TextButton(onClick = {
                                    selected = model.id
                                    preferenceManager.putString("local_model", model.id)
                                }) { Text(UiText.get(R.string.waex_ui_use_c36d819e, "Use")) }
                            }
                            TextButton(onClick = {
                                // Deleting waits for a running transcription to finish: not on the UI thread.
                                scope.launch { withContext(Dispatchers.IO) { LocalModelStore.delete(context, model) } }
                            }) { Text(UiText.get(R.string.waex_ui_delete_e2d0a549, "Delete"), color = colors.error) }
                        }
                    }
                }
                if (progress?.running == true) {
                    val fraction = if (progress.totalBytes > 0)
                        progress.downloadedBytes.toFloat() / progress.totalBytes else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(text = "${(fraction * 100).toInt()} %", style = typography.labelSm,
                        color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
