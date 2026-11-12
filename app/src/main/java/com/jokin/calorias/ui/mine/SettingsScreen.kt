package com.jokin.calorias.ui.mine

import android.Manifest
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.jokin.calorias.R
import com.jokin.calorias.data.health.HealthAvailability
import com.jokin.calorias.data.remote.ClaudeClient
import com.jokin.calorias.data.settings.Accent
import com.jokin.calorias.data.settings.AiProvider
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.data.settings.LabelMode
import com.jokin.calorias.data.settings.ThemeMode
import com.jokin.calorias.data.settings.WaterUnit
import com.jokin.calorias.domain.MascotKind
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.mascot.Mascot
import com.jokin.calorias.ui.mascot.Pose
import com.jokin.calorias.ui.theme.color
import com.jokin.calorias.ui.theme.rgbWheel
import com.jokin.calorias.work.ReminderScheduler

/** Subscription models that worked well for parsing Spanish food phrases (October 2026). */
// Only models that accept images and were checked reading a real label; the same model also serves voice.
private val SUGGESTED_MODELS = listOf(AppSettings.DEFAULT_NANO_MODEL, "z-ai/glm-5.3-flash", "google/gemma-4-31b-it")

@Composable
fun SettingsScreen() {
    val c = appContainer()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val s by remember { c.settings.settings }.collectAsState(initial = AppSettings())
    val hasNanoKey by remember { c.settings.hasNanoKey }.collectAsState(initial = false)
    val hasClaudeKey by remember { c.settings.hasClaudeKey }.collectAsState(initial = false)
    val hasKey = if (s.aiProvider == AiProvider.CLAUDE) hasClaudeKey else hasNanoKey

    fun update(transform: (AppSettings) -> AppSettings) = scope.runAction(snackbar) {
        c.settings.update(transform)
        ReminderScheduler.apply(context, c.settings.current())
    }

    var granted by remember { mutableIntStateOf(0) }
    var hcRefresh by remember { mutableIntStateOf(0) }
    val availability = remember { c.health.availability() }
    LaunchedEffect(hcRefresh) {
        if (availability == HealthAvailability.AVAILABLE) granted = c.health.grantedPermissions().count { it in c.health.allPermissions }
    }
    val hcPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { hcRefresh++ }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) scope.runAction(snackbar) { snackbar.showSnackbar(resources.getString(R.string.mine_settings_no_notification_permission)) }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.mine_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        LanguageSection()

        SectionCard {
            SectionTitle(stringResource(R.string.mine_settings_appearance))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(selected = s.themeMode == m, onClick = { update { it.copy(themeMode = m) } }, label = { Text(stringResource(m.labelRes)) }) }
            }
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Accent.entries.forEach { a ->
                    val fill = if (a == Accent.RGB) Brush.sweepGradient(rgbWheel(dark)) else SolidColor(a.color())
                    val chosen = s.accent == a && !s.dynamicColor
                    val name = stringResource(a.labelRes)
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(fill)
                            .border(3.dp, if (chosen) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .clickable { update { it.copy(accent = a, dynamicColor = false) } }
                            .semantics { contentDescription = name },
                    )
                }
            }
            Muted(stringResource(if (s.dynamicColor) R.string.mine_settings_dynamic_color else s.accent.labelRes))
            SwitchRow(stringResource(R.string.mine_settings_dynamic_color_switch), s.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.mine_settings_mascot))
            Muted(stringResource(R.string.mine_settings_mascot_hint))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MascotKind.entries.forEach { k ->
                    val selected = s.mascot == k
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                            .clickable { update { it.copy(mascot = k) } }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Mascot(k, if (selected) Pose.HAPPY else Pose.NEUTRAL, null, Modifier.size(72.dp), jumpKey = k.takeIf { selected })
                        Text(stringResource(k.labelRes()), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.mine_settings_water))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaterUnit.entries.forEach { u ->
                    FilterChip(selected = s.waterUnit == u, onClick = { update { it.copy(waterUnit = u) } }, label = { Text(stringResource(u.labelRes)) })
                }
            }
            var glass by remember(s.glassMl) { mutableStateOf(s.glassMl.toString()) }
            var goal by remember(s.waterGoalMl) { mutableStateOf(s.waterGoalMl.toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(glass, { glass = it }, stringResource(R.string.mine_settings_glass), Modifier.weight(1f), suffix = "ml", decimals = false)
                NumberField(goal, { goal = it }, stringResource(R.string.mine_settings_daily_goal), Modifier.weight(1f), suffix = "ml", decimals = false)
            }
            val g = glass.toIntOrNull(); val wg = goal.toIntOrNull()
            if (g != s.glassMl || wg != s.waterGoalMl) {
                TextButton(
                    onClick = { update { it.copy(glassMl = g!!, waterGoalMl = wg!!) } },
                    enabled = g != null && g in 50..1000 && wg != null && wg in 250..8000,
                ) { Text(stringResource(R.string.mine_settings_save_water)) }
            }
            Muted(stringResource(R.string.mine_settings_water_hint))
        }

        SectionCard {
            SectionTitle(stringResource(R.string.mine_settings_ai))
            Muted(stringResource(R.string.mine_settings_ai_hint))
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p -> FilterChip(selected = s.aiProvider == p, onClick = { update { it.copy(aiProvider = p) } }, label = { Text(p.label) }) }
            }
            val claude = s.aiProvider == AiProvider.CLAUDE
            Muted(stringResource(if (claude) R.string.mine_settings_claude_key_hint else R.string.mine_settings_nano_key_hint))
            var key by remember(s.aiProvider) { mutableStateOf("") }
            OutlinedTextField(
                key, { key = it.trim().take(300) },
                label = { Text(stringResource(if (hasKey) R.string.mine_settings_key_saved_hint else R.string.mine_settings_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val keySaved = stringResource(R.string.mine_settings_key_saved)
                val keyDeleted = stringResource(R.string.mine_settings_key_deleted)
                FilledTonalButton(
                    onClick = { scope.runAction(snackbar, keySaved) { if (claude) c.settings.setClaudeKey(key) else c.settings.setNanoKey(key); key = "" } },
                    enabled = key.length >= 10,
                ) { Text(stringResource(R.string.mine_settings_save_key)) }
                if (hasKey) {
                    OutlinedButton(onClick = { scope.runAction(snackbar, keyDeleted) { if (claude) c.settings.setClaudeKey(null) else c.settings.setNanoKey(null) } }) {
                        Text(stringResource(R.string.mine_delete))
                    }
                }
            }
            val savedModel = if (claude) s.claudeModel else s.nanoModel
            var model by remember(s.aiProvider, savedModel) { mutableStateOf(savedModel) }
            if (claude) {
                Column(Modifier.padding(top = 4.dp)) {
                    ClaudeClient.MODELS.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { model = m.id; update { it.copy(claudeModel = m.id) } }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = savedModel == m.id, onClick = { model = m.id; update { it.copy(claudeModel = m.id) } })
                            Column {
                                Text(m.label)
                                Muted(stringResource(m.note))
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(model, { model = it.trim().take(100) }, label = { Text(stringResource(R.string.mine_settings_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTED_MODELS.forEach { m -> FilterChip(selected = model == m, onClick = { model = m }, label = { Text(m.substringAfter('/')) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!claude && model != s.nanoModel) TextButton(onClick = { update { it.copy(nanoModel = model) } }, enabled = model.isNotBlank()) {
                    Text(stringResource(R.string.mine_settings_save_model))
                }
                OutlinedButton(
                    onClick = {
                        scope.runAction(snackbar) {
                            val k = (if (claude) c.settings.claudeKey() else c.settings.nanoKey()) ?: error(resources.getString(R.string.mine_settings_save_key_first))
                            snackbar.showSnackbar(c.ai.client(s.aiProvider).testConnection(k, model))
                        }
                    },
                    enabled = hasKey,
                ) { Text(stringResource(R.string.mine_settings_test_connection)) }
            }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.mine_settings_label_photo))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelMode.entries.forEach { m ->
                    FilterChip(selected = s.labelMode == m, onClick = { update { it.copy(labelMode = m) } }, label = { Text(stringResource(m.labelRes)) })
                }
            }
            Muted(
                when (s.labelMode) {
                    LabelMode.BASIC -> stringResource(R.string.mine_settings_label_basic_hint)
                    LabelMode.AI -> stringResource(R.string.mine_settings_label_ai_hint, s.aiProvider.label)
                }
            )
            if (s.labelMode == LabelMode.AI && !hasKey) {
                Text(stringResource(R.string.mine_settings_label_missing_key, s.aiProvider.label), color = MaterialTheme.colorScheme.error)
            }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.mine_settings_reminders))
            SwitchRow(stringResource(R.string.mine_settings_reminders_switch), s.remindersEnabled) { v ->
                if (v) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                update { it.copy(remindersEnabled = v) }
            }
            var hour by remember(s.reminderHour) { mutableStateOf(s.reminderHour.toString()) }
            var minute by remember(s.reminderMinute) { mutableStateOf("%02d".format(s.reminderMinute)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(hour, { hour = it }, stringResource(R.string.mine_settings_hour), Modifier.weight(1f), decimals = false)
                NumberField(minute, { minute = it }, stringResource(R.string.mine_settings_minute), Modifier.weight(1f), decimals = false)
            }
            val h = hour.toIntOrNull(); val m = minute.toIntOrNull()
            if (h != s.reminderHour || m != s.reminderMinute) {
                TextButton(onClick = { update { it.copy(reminderHour = h!!, reminderMinute = m!!) } }, enabled = h in 0..23 && m in 0..59) {
                    Text(stringResource(R.string.mine_settings_save_time))
                }
            }
            Muted(stringResource(R.string.mine_settings_battery_hint))
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) { Text(stringResource(R.string.mine_settings_open_app_settings)) }
        }

        SectionCard {
            SectionTitle("Health Connect")
            when (availability) {
                HealthAvailability.AVAILABLE -> {
                    Text(stringResource(R.string.mine_settings_hc_granted, granted, c.health.allPermissions.size))
                    Muted(stringResource(R.string.mine_settings_hc_hint))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { hcPermissions.launch(c.health.allPermissions) }) { Text(stringResource(R.string.mine_settings_hc_grant)) }
                        OutlinedButton(onClick = { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }) {
                            Text(stringResource(R.string.mine_settings_hc_open))
                        }
                    }
                }
                HealthAvailability.NEEDS_UPDATE -> Muted(stringResource(R.string.mine_settings_hc_needs_update))
                HealthAvailability.UNAVAILABLE -> Muted(stringResource(R.string.mine_settings_hc_unavailable))
            }
        }

        CreditsSection()

        val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
        Muted("${stringResource(R.string.app_name)} ${info.versionName} (${info.longVersionCode})", Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Language names are shown in their own language, so they stay readable whatever is selected. */
private val APP_LANGUAGES = listOf("es" to "Español", "en" to "English")

/** Android 13+ stores the per-app language itself and recreates the activity when it changes. */
@Composable
private fun LanguageSection() {
    val localeManager = LocalContext.current.getSystemService(LocaleManager::class.java)
    var selected by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }
    fun choose(tag: String) {
        localeManager.applicationLocales = LocaleList.forLanguageTags(tag)
        selected = tag
    }
    SectionCard {
        SectionTitle(stringResource(R.string.settings_language))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected.isEmpty(), onClick = { choose("") }, label = { Text(stringResource(R.string.settings_language_system)) })
            APP_LANGUAGES.forEach { (tag, name) ->
                FilterChip(selected = selected == tag, onClick = { choose(tag) }, label = { Text(name) })
            }
        }
        Muted(stringResource(R.string.settings_language_hint))
    }
}

/** Attribution the data licences ask for (CIQUAL, Open Food Facts' ODbL), where users see the data. */
private val CREDITS = listOf(
    R.string.mine_credits_ciqual to "https://ciqual.anses.fr",
    R.string.mine_credits_usda to "https://fdc.nal.usda.gov",
    R.string.mine_credits_off to "https://world.openfoodfacts.org",
    R.string.mine_credits_font to "https://openfontlicense.org",
    R.string.mine_credits_app to "https://github.com/YOKINGWAPO/migas",
)

@Composable
private fun CreditsSection() {
    val uriHandler = LocalUriHandler.current
    SectionCard {
        SectionTitle(stringResource(R.string.mine_settings_credits))
        CREDITS.forEach { (text, url) ->
            Muted(stringResource(text))
            TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(0.dp)) {
                Text(url.removePrefix("https://"))
            }
        }
    }
}
