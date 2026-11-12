package com.jokin.calorias.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.text.format.DateFormat
import com.jokin.calorias.R
import com.jokin.calorias.domain.DayValue
import com.jokin.calorias.domain.Trends
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A4 summary for a nutritionist: averages, weight chart and one row per day. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 40f
    private const val ROW = 18f
    /** Texts, dates and decimals follow the app's language, which is the locale of [context]'s resources. */
    fun write(context: Context, target: File, from: Long, to: Long, days: List<DaySummary>) {
        val locale = context.resources.configuration.locales[0]
        val dayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
        val rangeFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val doc = PdfDocument()
        try {
            val text = Paint().apply { color = Color.rgb(30, 30, 30); textSize = 10f; isAntiAlias = true }
            val bold = Paint(text).apply { isFakeBoldText = true }
            val title = Paint(bold).apply { textSize = 18f }
            val muted = Paint(text).apply { color = Color.rgb(110, 110, 110) }

            var pageNumber = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNumber).create())
            var canvas = page.canvas
            var y = MARGIN + 10

            canvas.drawText(context.getString(R.string.mine_pdf_title), MARGIN, y, title)
            y += 20
            canvas.drawText(
                context.getString(R.string.mine_pdf_range, LocalDate.ofEpochDay(from).format(rangeFormat), LocalDate.ofEpochDay(to).format(rangeFormat)),
                MARGIN, y, muted,
            )
            y += 26

            val logged = days.filter { it.nutrients.kcal > 0 }
            if (logged.isNotEmpty()) {
                val avg = { f: (DaySummary) -> Double -> logged.map(f).average() }
                canvas.drawText(context.resources.getQuantityString(R.plurals.mine_pdf_average, logged.size, logged.size), MARGIN, y, bold)
                y += 16
                canvas.drawText(
                    context.getString(R.string.mine_pdf_averages,
                        avg { it.nutrients.kcal }, avg { it.nutrients.protein }, avg { it.nutrients.carbs }, avg { it.nutrients.fat },
                        (days.filter { it.waterMl > 0 }.map { it.waterMl }.takeIf { it.isNotEmpty() }?.average() ?: 0.0) / 1000),
                    MARGIN, y, text,
                )
                y += 24
            }

            val weights = days.mapNotNull { d -> d.weightKg?.let { DayValue(d.epochDay, it) } }
            if (weights.size >= 2) {
                canvas.drawText(context.getString(R.string.mine_pdf_weight_chart), MARGIN, y, bold)
                y += 8
                drawWeightChart(canvas, weights, MARGIN, y, W - 2 * MARGIN, 150f, muted, locale)
                y += 170
            }

            val columns = listOf(
                R.string.mine_pdf_column_day to 0f, R.string.mine_pdf_column_kcal to 110f, R.string.mine_pdf_column_protein to 160f,
                R.string.mine_pdf_column_carbs to 205f, R.string.mine_pdf_column_fat to 250f, R.string.mine_pdf_column_water to 295f,
                R.string.mine_pdf_column_weight to 340f, R.string.mine_pdf_column_note to 390f,
            ).map { (label, x) -> context.getString(label) to x }
            fun header() {
                columns.forEach { (label, x) -> canvas.drawText(label, MARGIN + x, y, bold) }
                y += ROW
            }
            header()
            for (d in days) {
                if (y > H - MARGIN) {
                    doc.finishPage(page)
                    pageNumber++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNumber).create())
                    canvas = page.canvas
                    y = MARGIN + 10
                    header()
                }
                val n = d.nutrients
                val values = listOf(
                    LocalDate.ofEpochDay(d.epochDay).format(dayFormat),
                    if (n.kcal > 0) "%.0f".format(n.kcal) else "—",
                    if (n.kcal > 0) "%.0f".format(n.protein) else "",
                    if (n.kcal > 0) "%.0f".format(n.carbs) else "",
                    if (n.kcal > 0) "%.0f".format(n.fat) else "",
                    if (d.waterMl > 0) String.format(locale, "%.1f L", d.waterMl / 1000.0) else "",
                    d.weightKg?.let { String.format(locale, "%.1f", it) } ?: "",
                    d.note?.take(30) ?: "",
                )
                values.zip(columns).forEach { (v, c) -> canvas.drawText(v, MARGIN + c.second, y, text) }
                y += ROW
            }
            doc.finishPage(page)
            target.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun drawWeightChart(canvas: Canvas, weights: List<DayValue>, x: Float, top: Float, width: Float, height: Float, label: Paint, locale: Locale) {
        val trend = Trends.movingAverage7(weights)
        val minDay = weights.minOf { it.epochDay }
        val maxDay = weights.maxOf { it.epochDay }.coerceAtLeast(minDay + 1)
        val minW = weights.minOf { it.value } - 0.5
        val maxW = weights.maxOf { it.value } + 0.5
        fun px(day: Long) = x + (day - minDay).toFloat() / (maxDay - minDay) * width
        fun py(w: Double) = top + height - ((w - minW) / (maxW - minW)).toFloat() * height

        val axis = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1f }
        canvas.drawLine(x, top + height, x + width, top + height, axis)
        canvas.drawText(String.format(locale, "%.1f", maxW), x, top + 10, label)
        canvas.drawText(String.format(locale, "%.1f", minW), x, top + height - 2, label)

        val dot = Paint().apply { color = Color.rgb(120, 160, 140); isAntiAlias = true }
        weights.forEach { canvas.drawCircle(px(it.epochDay), py(it.value), 2.5f, dot) }

        val line = Paint().apply { color = Color.rgb(46, 125, 91); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
        val path = Path()
        trend.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.epochDay), py(p.value)) else path.lineTo(px(p.epochDay), py(p.value)) }
        canvas.drawPath(path, line)
    }
}
