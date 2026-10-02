package com.kurupdevs.karz.ui.simulator

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.MediaStore
import com.kurupdevs.karz.math.OverpaymentSimulation
import com.kurupdevs.karz.math.PrepayStrategy
import com.kurupdevs.karz.math.formatDuration
import com.kurupdevs.karz.math.formatMoney
import com.kurupdevs.karz.math.formatMoneyCompact
import java.time.format.DateTimeFormatter

/**
 * Renders the shareable result card as a bitmap (1080x1350, story-friendly)
 * and fires the Android Sharesheet with the card plus a text summary.
 *
 * The bitmap goes through MediaStore, so no FileProvider manifest entry is
 * needed. If the insert fails (e.g. old API without storage permission), it
 * falls back to a text-only share instead of crashing.
 */
object ShareCardRenderer {

    private const val W = 1080
    private const val H = 1350

    private val navy = android.graphics.Color.parseColor("#14141F")
    private val purpleStart = android.graphics.Color.parseColor("#7C6CF5")
    private val purpleEnd = android.graphics.Color.parseColor("#4A3FD1")
    private val white = android.graphics.Color.WHITE
    private val muted = android.graphics.Color.parseColor("#A7A7C0")
    private val cardBg = android.graphics.Color.parseColor("#23233F")

    fun render(
        loan: SimulatorLoan,
        extraPerMonthMinor: Long,
        lumpMinor: Long,
        result: OverpaymentSimulation,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dateFmt = DateTimeFormatter.ofPattern("MMM yyyy")

        // Background.
        c.drawColor(navy)

        // Top gradient band.
        val bandPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, W.toFloat(), 420f,
                purpleStart, purpleEnd, Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), 430f, bandPaint)

        fun textPaint(size: Float, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            this.color = color
        }

        val title = textPaint(44f, true, white)
        val big = textPaint(120f, true, white)
        val mid = textPaint(52f, true, white)
        val label = textPaint(34f, false, muted)
        val small = textPaint(30f, false, muted)
        val dark = textPaint(40f, true, white)

        var y = 130f
        c.drawText("Karz", 80f, y, title)
        y += 90f
        val stratName = if (result.strategy == PrepayStrategy.REDUCE_TENURE) "Tenure cut" else "EMI cut"
        c.drawText("Prepayment plan  ·  $stratName", 80f, y, textPaint(36f, false, white))
        y += 110f

        // Hero numbers.
        c.drawText("FINISH EARLY BY", 80f, y, label); y += 130f
        c.drawText(formatDuration(result.monthsEarly), 80f, y, big); y += 70f
        c.drawText("new payoff ${result.newPayoffDate.format(dateFmt)}", 80f, y, small); y += 130f

        c.drawText("INTEREST SAVED", 80f, y, label); y += 130f
        c.drawText(formatMoneyCompact(result.interestSavedMinor, loan.currencyCode), 80f, y, big); y += 60f
        c.drawText(formatMoney(result.interestSavedMinor, loan.currencyCode), 80f, y, small); y += 120f

        // Detail card.
        val card = RectF(80f, y, (W - 80).toFloat(), (H - 150).toFloat())
        c.drawRoundRect(card, 36f, 36f, Paint().apply { color = cardBg })
        var cy = y + 90f
        val pad = 130f
        fun row(k: String, v: String) {
            c.drawText(k, pad, cy, small)
            val w = textPaint(38f, true, white).measureText(v)
            c.drawText(v, W - pad - w, cy, dark.apply { textSize = 38f })
            cy += 78f
        }
        val extraLine = buildString {
            append(formatMoneyCompact(extraPerMonthMinor, loan.currencyCode))
            append("/mo")
            if (lumpMinor > 0) append(" + ${formatMoneyCompact(lumpMinor, loan.currencyCode)} lump")
        }
        row("Extra payment", extraLine)
        row("Lender", loan.lenderName.take(18))
        if (result.strategy == PrepayStrategy.REDUCE_EMI) {
            row("New EMI", formatMoneyCompact(result.newEmiMinor, loan.currencyCode))
        } else {
            row("EMI stays", formatMoneyCompact(result.baseEmiMinor, loan.currencyCode))
        }
        row("Was paying till", result.basePayoffDate.format(dateFmt))

        // Footer.
        c.drawText(
            "Estimated from my own loan entries. Confirm with your lender.",
            80f, (H - 70).toFloat(), textPaint(28f, false, muted),
        )
        return bmp
    }

    /** Shares the card image + text summary. Returns false only if nothing could be shared. */
    fun share(
        context: Context,
        loan: SimulatorLoan,
        extraPerMonthMinor: Long,
        lumpMinor: Long,
        result: OverpaymentSimulation,
        text: String,
    ): Boolean {
        val uri = try {
            val bmp = render(loan, extraPerMonthMinor, lumpMinor, result)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "karz-prepay-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            val resolver = context.contentResolver
            val inserted = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (inserted != null) {
                resolver.openOutputStream(inserted)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            inserted
        } catch (_: Exception) {
            null
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Share your plan"))
            true
        } catch (_: Exception) {
            false
        }
    }
}
