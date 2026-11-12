package com.jokin.calorias.ui.mine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.export.ExportFormat
import com.jokin.calorias.data.export.ExportKind
import com.jokin.calorias.data.export.ExportRequest
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.shortDate
import com.jokin.calorias.ui.common.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private enum class Range(@StringRes val labelRes: Int, val days: Int?) {
    WEEK(R.string.mine_export_range_week, 7),
    MONTH(R.string.mine_export_range_month, 30),
    YEAR(R.string.mine_export_range_year, 365),
    ALL(R.string.mine_export_range_all, null),
}

/** Imports larger than this are not backups of this app; refuse instead of reading them into memory. */
private const val MAX_IMPORT_BYTES = 50L * 1024 * 1024

@Composable
fun ExportScreen(nav: NavController) {
    val c = appContainer()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var kinds by rememberSaveable { mutableStateOf(ExportKind.entries.map { it.name }.toSet()) }
    var range by rememberSaveable { mutableStateOf(Range.MONTH) }
    var format by rememberSaveable { mutableStateOf(ExportFormat.CSV) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val pickPhotoArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.runAction(snackbar) {
            try {
                val r = c.body.importPhotos(uri)
                snackbar.showSnackbar(
                    if (r.ignored > 0) resources.getString(R.string.mine_export_photos_imported_ignored, r.added, r.alreadyThere, r.ignored)
                    else resources.getString(R.string.mine_export_photos_imported, r.added, r.alreadyThere)
                )
            } finally { busy = false }
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.runAction(snackbar) {
            pendingImport = withContext(Dispatchers.IO) {
                val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
                if (size > MAX_IMPORT_BYTES) throw IOException(resources.getString(R.string.mine_export_backup_too_big))
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw IOException(resources.getString(R.string.mine_open_failed))
            }
        }
    }

    suspend fun build(): File {
        val to = today()
        val from = range.days?.let { to - it + 1 } ?: (c.log.loggedDays().minOrNull() ?: to)
        return c.export.export(ExportRequest(kinds.map { ExportKind.valueOf(it) }.toSet(), from, to, format))
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.mine_export), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        SectionCard {
            SectionTitle(stringResource(R.string.mine_export_format))
            ExportFormat.entries.forEach { f ->
                Row(Modifier.fillMaxWidth().selectable(selected = format == f, onClick = { format = f }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = format == f, onClick = { format = f })
                    Column { Text(stringResource(f.labelRes)); Muted(stringResource(f.descriptionRes)) }
                }
            }
        }
        if (format != ExportFormat.JSON) {
            SectionCard {
                SectionTitle(stringResource(R.string.mine_export_what))
                if (format == ExportFormat.PDF) Muted(stringResource(R.string.mine_export_pdf_hint))
                else ExportKind.entries.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = k.name in kinds, onCheckedChange = { on -> kinds = if (on) kinds + k.name else kinds - k.name })
                        Text(stringResource(k.labelRes))
                    }
                }
            }
            SectionCard {
                SectionTitle(stringResource(R.string.mine_export_dates))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Range.entries.forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(stringResource(r.labelRes)) }) }
                }
                range.days?.let { Muted(stringResource(R.string.mine_export_since, shortDate(today() - it + 1))) }
            }
        } else {
            Muted(stringResource(R.string.mine_export_json_hint))
        }

        val canExport = !busy && (format != ExportFormat.CSV || kinds.isNotEmpty())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                busy = true
                scope.runAction(snackbar) {
                    try { share(context, build(), format.mime) } finally { busy = false }
                }
            }, enabled = canExport) { Text(stringResource(R.string.mine_share)) }
            FilledTonalButton(onClick = {
                busy = true
                scope.runAction(snackbar) {
                    try {
                        val f = build()
                        saveToDownloads(context, f, format.mime)
                        snackbar.showSnackbar(resources.getString(R.string.mine_export_saved_to_downloads, f.name))
                    } finally { busy = false }
                }
            }, enabled = canExport) { Text(stringResource(R.string.mine_export_save_to_downloads)) }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.mine_export_photos))
            Muted(stringResource(R.string.mine_export_photos_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    busy = true
                    scope.runAction(snackbar) { try { share(context, c.body.exportPhotos(), "application/zip") } finally { busy = false } }
                }, enabled = !busy) { Text(stringResource(R.string.mine_share)) }
                OutlinedButton(onClick = {
                    busy = true
                    scope.runAction(snackbar) {
                        try {
                            val f = c.body.exportPhotos()
                            saveToDownloads(context, f, "application/zip")
                            snackbar.showSnackbar(resources.getString(R.string.mine_export_saved_to_downloads, f.name))
                        } finally { busy = false }
                    }
                }, enabled = !busy) { Text(stringResource(R.string.mine_export_to_downloads)) }
            }
            OutlinedButton(onClick = { pickPhotoArchive.launch(arrayOf("application/zip", "*/*")) }, enabled = !busy) {
                Text(stringResource(R.string.mine_export_import_photos))
            }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.import_title))
            Muted(stringResource(R.string.import_entry_hint))
            OutlinedButton(onClick = { nav.navigate(Routes.IMPORT) }) { Text(stringResource(R.string.import_entry_button)) }
        }

        SectionCard {
            SectionTitle(stringResource(R.string.mine_export_import_backup))
            Muted(stringResource(R.string.mine_export_import_backup_hint))
            OutlinedButton(onClick = { pickBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Text(stringResource(R.string.mine_export_choose_file))
            }
        }
    }

    pendingImport?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.mine_export_restore_question)) },
            text = { Text(stringResource(R.string.mine_export_restore_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.runAction(snackbar) { snackbar.showSnackbar(c.export.restore(json)) }
                }) { Text(stringResource(R.string.mine_export_restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.mine_cancel)) } },
        )
    }
}

private fun share(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.mine_export_chooser)))
}

private suspend fun saveToDownloads(context: Context, file: File, mime: String) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException(context.getString(R.string.mine_export_downloads_create_failed))
    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException(context.getString(R.string.mine_export_downloads_write_failed))
}
