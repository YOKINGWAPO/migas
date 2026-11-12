package com.jokin.calorias.ui.mine

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.jokin.calorias.R
import com.jokin.calorias.data.export.DiaryPreview
import com.jokin.calorias.data.health.HealthAvailability
import com.jokin.calorias.data.health.HealthRepository
import com.jokin.calorias.domain.CsvImport
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.MealSelector
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.appLocale
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.shortDate
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bytes read before giving up; a UTF-8 file this size is already past [CsvImport.MAX_CHARS] for any real export. */
private const val MAX_FILE_BYTES = 6L * 1024 * 1024
private const val ERRORS_SHOWN = 8

private sealed interface Pending {
    data class Diary(val preview: DiaryPreview, val errors: List<CsvImport.RowError>) : Pending
    data class Weights(val rows: List<CsvImport.WeightRow>, val errors: List<CsvImport.RowError>) : Pending
}

/** A CSV from another app or a spreadsheet: pick it, check the preview, then import. */
@Composable
fun ImportScreen() {
    val c = appContainer()
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pending?>(null) }
    var skipLogged by remember { mutableStateOf(true) }
    var defaultMeal by remember { mutableStateOf(MealType.SNACK) }
    var busy by remember { mutableStateOf(false) }

    fun importWeights(rows: List<CsvImport.WeightRow>) = scope.runAction(snackbar) {
        if (!c.imports.importWeights(rows)) throw IOException(res.getString(R.string.import_weight_permission))
        pending = null
        snackbar.showSnackbar(res.getString(R.string.import_weight_done, rows.map { it.epochDay }.distinct().size))
    }

    val weightPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val p = pending as? Pending.Weights ?: return@rememberLauncherForActivityResult
        if (HealthRepository.WEIGHT_WRITE_PERMISSION in granted) importWeights(p.rows)
        else scope.runAction(snackbar) { snackbar.showSnackbar(res.getString(R.string.import_weight_permission)) }
    }

    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.runAction(snackbar) {
            try {
                pending = null
                val parsed = try {
                    withContext(Dispatchers.Default) { CsvImport.parse(readText(context, uri), LocalDate.now()) }
                } catch (e: CsvImport.ImportException) {
                    throw IOException(res.getString(fileProblemText(e.problem)))
                }
                pending = when (parsed) {
                    is CsvImport.Parsed.Diary -> Pending.Diary(c.imports.previewDiary(parsed), parsed.errors)
                    is CsvImport.Parsed.Weights -> Pending.Weights(parsed.rows, parsed.errors)
                }
            } finally { busy = false }
        }
    }

    var templateText by remember { mutableStateOf("") }
    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.runAction(snackbar, res.getString(R.string.import_template_saved)) {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(templateText.toByteArray(Charsets.UTF_8)) }
                    ?: throw IOException(res.getString(R.string.import_open_failed))
            }
        }
    }
    val english = appLocale().language != "es"

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        SectionCard {
            Muted(stringResource(R.string.import_intro))
            Muted(stringResource(R.string.import_diary_columns))
            Muted(stringResource(R.string.import_weight_columns))
            Button(onClick = { pickCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }, enabled = !busy) {
                Text(stringResource(R.string.import_pick))
            }
            val diaryFile = stringResource(R.string.import_template_diary_file)
            val weightFile = stringResource(R.string.import_template_weight_file)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { templateText = CsvImport.diaryTemplate(english); saveTemplate.launch(diaryFile) }) {
                    Text(stringResource(R.string.import_template_diary))
                }
                OutlinedButton(onClick = { templateText = CsvImport.weightTemplate(english); saveTemplate.launch(weightFile) }) {
                    Text(stringResource(R.string.import_template_weight))
                }
            }
        }

        when (val p = pending) {
            is Pending.Diary -> SectionCard {
                SectionTitle(stringResource(R.string.import_preview_diary))
                val rows = p.preview.toImport(skipLogged)
                RowSummary(rows.size, rows.map { it.epochDay }.toSet(), p.errors)
                if (p.preview.alreadyLogged.isNotEmpty()) {
                    Muted(stringResource(R.string.import_already_logged, p.preview.alreadyLogged.size))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.import_skip_logged), Modifier.weight(1f))
                        Switch(checked = skipLogged, onCheckedChange = { skipLogged = it })
                    }
                }
                val withoutMeal = rows.count { it.meal == null }
                if (withoutMeal > 0) {
                    Muted(stringResource(R.string.import_without_meal, withoutMeal))
                    MealSelector(defaultMeal, { defaultMeal = it })
                }
                Button(onClick = {
                    busy = true
                    scope.runAction(snackbar) {
                        try {
                            val r = c.imports.importDiary(rows, defaultMeal)
                            pending = null
                            snackbar.showSnackbar(res.getString(R.string.import_diary_done, r.entries, r.days, r.foods))
                        } finally { busy = false }
                    }
                }, enabled = !busy && rows.isNotEmpty()) { Text(stringResource(R.string.import_button)) }
            }
            is Pending.Weights -> SectionCard {
                SectionTitle(stringResource(R.string.import_preview_weight))
                RowSummary(p.rows.size, p.rows.map { it.epochDay }.toSet(), p.errors)
                val available = remember { c.health.availability() == HealthAvailability.AVAILABLE }
                if (!available) Muted(stringResource(R.string.import_hc_missing))
                Button(onClick = {
                    scope.runAction(snackbar) {
                        if (c.imports.canWriteWeight()) importWeights(p.rows)
                        else weightPermission.launch(setOf(HealthRepository.WEIGHT_WRITE_PERMISSION))
                    }
                }, enabled = !busy && available && p.rows.isNotEmpty()) { Text(stringResource(R.string.import_button)) }
            }
            null -> {}
        }
    }
}

@Composable
private fun RowSummary(rows: Int, days: Set<Long>, errors: List<CsvImport.RowError>) {
    if (rows == 0) Muted(stringResource(R.string.import_nothing))
    else {
        Text(stringResource(R.string.import_valid_rows, rows, days.size))
        Muted(stringResource(R.string.import_range, shortDate(days.min()), shortDate(days.max())))
    }
    if (errors.isEmpty()) return
    Text(stringResource(R.string.import_errors, errors.size), color = MaterialTheme.colorScheme.error)
    errors.take(ERRORS_SHOWN).forEach { e -> Muted(stringResource(R.string.import_error_line, e.line, rowProblemText(e))) }
    if (errors.size > ERRORS_SHOWN) Muted(stringResource(R.string.import_more_errors, errors.size - ERRORS_SHOWN))
}

@Composable
private fun rowProblemText(e: CsvImport.RowError): String {
    val column = e.column?.let { stringResource(columnName(it)) }.orEmpty()
    return when (e.problem) {
        CsvImport.RowProblem.BAD_DATE -> stringResource(R.string.import_problem_bad_date)
        CsvImport.RowProblem.FUTURE_DATE -> stringResource(R.string.import_problem_future_date)
        CsvImport.RowProblem.BAD_NUMBER -> stringResource(R.string.import_problem_bad_number, column)
        CsvImport.RowProblem.OUT_OF_RANGE -> stringResource(R.string.import_problem_out_of_range, column)
        CsvImport.RowProblem.MISSING_NAME -> stringResource(R.string.import_problem_missing_name)
        CsvImport.RowProblem.MISSING_KCAL -> stringResource(R.string.import_problem_missing_kcal)
        CsvImport.RowProblem.TOO_MANY_KCAL_PER_100G -> stringResource(R.string.import_problem_too_many_kcal)
    }
}

private fun columnName(c: CsvImport.Column): Int = when (c) {
    CsvImport.Column.DATE -> R.string.import_col_date
    CsvImport.Column.TIME -> R.string.import_col_time
    CsvImport.Column.MEAL -> R.string.import_col_meal
    CsvImport.Column.NAME -> R.string.import_col_name
    CsvImport.Column.BRAND -> R.string.import_col_brand
    CsvImport.Column.GRAMS -> R.string.import_col_grams
    CsvImport.Column.KCAL -> R.string.import_col_kcal
    CsvImport.Column.PROTEIN -> R.string.import_col_protein
    CsvImport.Column.CARBS -> R.string.import_col_carbs
    CsvImport.Column.FAT -> R.string.import_col_fat
    CsvImport.Column.WEIGHT -> R.string.import_col_weight
}

private fun fileProblemText(p: CsvImport.FileProblem): Int = when (p) {
    CsvImport.FileProblem.EMPTY -> R.string.import_file_empty
    CsvImport.FileProblem.TOO_BIG -> R.string.import_file_too_big
    CsvImport.FileProblem.TOO_MANY_ROWS -> R.string.import_file_too_many_rows
    CsvImport.FileProblem.UNKNOWN_COLUMNS -> R.string.import_file_unknown_columns
}

/** Reads at most [MAX_FILE_BYTES]; a bigger file is refused before it is loaded into memory. */
private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
    if (size > MAX_FILE_BYTES) throw IOException(context.getString(R.string.import_file_too_big))
    resolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytes((MAX_FILE_BYTES + 1).toInt())
        if (bytes.size > MAX_FILE_BYTES) throw IOException(context.getString(R.string.import_file_too_big))
        decode(bytes)
    } ?: throw IOException(context.getString(R.string.import_open_failed))
}

/** Excel on Windows still saves CSV as Windows-1252; reading that as UTF-8 would break every accent. */
private fun decode(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
} catch (e: CharacterCodingException) {
    String(bytes, Charset.forName("windows-1252"))
}
