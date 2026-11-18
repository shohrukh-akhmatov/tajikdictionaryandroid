package tj.tajikdictionary.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import tj.tajikdictionary.R
import tj.tajikdictionary.core.AppLinks
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** One number on the card ("92%" over "Accuracy"). */
data class ShareStat(val value: String, val label: String)

/**
 * Everything a share card shows. Built with [ShareCardContent.round], [placement], [progress] or [badge]
 * (same cards and wording as iOS `ShareCardContent`).
 */
data class ShareCardContent(
    val symbol: ImageVector,
    val headline: String,
    val subtitle: String?,
    val stats: List<ShareStat>,
    val date: Date = Date(),
) {
    /** Text that goes with the image (many apps drop the image or the text, so both carry the link). */
    fun shareText(context: Context): String = buildList {
        add(headline)
        subtitle?.let(::add)
        add(stats.joinToString(" · ") { "${it.label}: ${it.value}" })
        add("${context.getString(R.string.share_cta)} ${AppLinks.HASHTAG}")
        add(AppLinks.SHARE_URL)
    }.joinToString("\n")
}

/** Renders the 1080×1350 card (4:5, fits Instagram/Telegram/WhatsApp) and hands it to the share sheet. */
object ShareCards {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    const val APP_NAME_EN = "Tajik Dictionary"
    const val APP_NAME_TG = "Луғати тоҷикӣ"
    const val TAJIK_TAGLINE = "Забони тоҷикиро меомӯзам!"

    // Fixed light palette so the card looks the same in light and dark mode.
    private const val ACCENT = 0xFF087653.toInt()
    private const val DEEP = 0xFF04392A.toInt()
    private const val MINT = 0xFF6FD8AC.toInt()
    private const val YELLOW = 0xFFFFCC00.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    // iOS lays the card out at 360×450 pt and renders at 3×; every size below is pt × 3.
    private const val PAD = 72f

    /**
     * Draws the card. [symbol] and [brandIcon] are pre-rasterised icons (see [ShareCardDialog]);
     * [ctaLine] is the localized "Learn Tajik for free, even offline".
     */
    fun render(
        content: ShareCardContent,
        ctaLine: String,
        symbol: Bitmap?,
        brandIcon: Bitmap?,
        locale: Locale = Locale.getDefault(),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inner = WIDTH - 2 * PAD

        c.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), ACCENT, DEEP, Shader.TileMode.CLAMP)
        })

        // Measure every block first so the free space can be split like SwiftUI spacers.
        val headerH = 90f
        val headlinePaint = paint(90f, WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        val headline = fittedLayout(content.headline, headlinePaint, inner, maxLines = 2, minScale = 0.6f)
        val subtitle = content.subtitle?.let {
            fittedLayout(it, paint(45f, WHITE, Typeface.DEFAULT_BOLD, alpha = 0.85f), inner, maxLines = 2, minScale = 0.7f)
        }
        val symbolH = if (symbol != null) 120f + 30f else 0f
        val titleBlockH = symbolH + headline.height + (subtitle?.let { 12f + it.height } ?: 0f)

        val stats = content.stats.take(4)
        val gap = 30f
        val tileW = (inner - gap) / 2
        val valueSize = 78f
        val labelSize = 33f
        val tileH = 36f + valueSize * 1.17f + 6f + labelSize * 1.2f + 36f
        val rows = (stats.size + 1) / 2
        val gridH = if (rows == 0) 0f else rows * tileH + (rows - 1) * gap

        val taglinePaint = paint(45f, MINT, Typeface.DEFAULT_BOLD)
        val taglineH = 45f * 1.2f
        val qrBox = 174f + 30f
        val footerH = qrBox

        val mins = floatArrayOf(54f, 48f, 48f, 36f)
        val used = headerH + titleBlockH + gridH + taglineH + footerH
        val extra = ((HEIGHT - 2 * PAD) - used - mins.sum()).coerceAtLeast(0f) / mins.size
        val spacers = mins.map { it + extra }

        // Header: app mark, names, date.
        var y = PAD
        c.drawRoundRect(RectF(PAD, y, PAD + 90f, y + 90f), 24f, 24f, fill(MINT))
        brandIcon?.let { c.drawBitmap(it, null, RectF(PAD + 22.5f, y + 22.5f, PAD + 67.5f, y + 67.5f), null) }
        val nameX = PAD + 90f + 24f
        c.drawText(APP_NAME_EN, nameX, y + 42f, paint(42f, WHITE, Typeface.DEFAULT_BOLD))
        c.drawText(APP_NAME_TG, nameX, y + 82f, paint(33f, WHITE, Typeface.DEFAULT, alpha = 0.75f))
        val datePaint = paint(33f, WHITE, Typeface.DEFAULT, alpha = 0.75f).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(content.date), WIDTH - PAD, y + 58f, datePaint)
        y += headerH + spacers[0]

        // Symbol, headline, subtitle.
        if (symbol != null) {
            c.drawBitmap(symbol, null, RectF(PAD, y, PAD + 120f, y + 120f), null)
            y += symbolH
        }
        c.save(); c.translate(PAD, y); headline.draw(c); c.restore()
        y += headline.height
        subtitle?.let {
            y += 12f
            c.save(); c.translate(PAD, y); it.draw(c); c.restore()
            y += it.height
        }
        y += spacers[1]

        // Stat tiles, 2 columns.
        val tileFill = fill(WHITE).apply { alpha = (0.13f * 255).toInt() }
        stats.forEachIndexed { i, stat ->
            val left = PAD + (i % 2) * (tileW + gap)
            val top = y + (i / 2) * (tileH + gap)
            c.drawRoundRect(RectF(left, top, left + tileW, top + tileH), 42f, 42f, tileFill)
            val textW = tileW - 72f
            val valuePaint = shrinkToFit(stat.value, paint(valueSize, WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD)), textW, 0.5f)
            c.drawText(stat.value, left + 36f, top + 36f + valueSize * 0.95f, valuePaint)
            val labelPaint = shrinkToFit(stat.label, paint(labelSize, WHITE, Typeface.DEFAULT_BOLD, alpha = 0.8f), textW, 0.7f)
            val label = TextUtils.ellipsize(stat.label, labelPaint, textW, TextUtils.TruncateAt.END).toString()
            c.drawText(label, left + 36f, top + 36f + valueSize * 1.17f + 6f + labelSize, labelPaint)
        }
        y += gridH + spacers[2]

        c.drawText(TAJIK_TAGLINE, PAD, y + 45f, taglinePaint)
        y += taglineH + spacers[3]

        // Footer: CTA + link on the left, QR code on the right (bottom-aligned).
        val footerBottom = HEIGHT - PAD
        val qrLeft = WIDTH - PAD - qrBox
        c.drawRoundRect(RectF(qrLeft, footerBottom - qrBox, WIDTH - PAD, footerBottom), 24f, 24f, fill(WHITE))
        qrCode(AppLinks.SHARE_URL, 174)?.let {
            c.drawBitmap(it, null, RectF(qrLeft + 15f, footerBottom - qrBox + 15f, qrLeft + 15f + 174f, footerBottom - 15f), null)
        }
        val textMax = qrLeft - 36f - PAD
        val urlPaint = shrinkToFit(AppLinks.displayUrl, paint(33f, WHITE, Typeface.MONOSPACE, alpha = 0.8f), textMax, 0.6f)
        c.drawText(AppLinks.displayUrl, PAD, footerBottom - 10f, urlPaint)
        val ctaPaint = shrinkToFit(ctaLine, paint(36f, WHITE, Typeface.DEFAULT_BOLD), textMax, 0.7f)
        c.drawText(ctaLine, PAD, footerBottom - 10f - 33f * 1.2f - 6f, ctaPaint)
        return bmp
    }

    /** QR code (error correction M, like iOS) as a crisp black-on-white bitmap of [size]×[size]. */
    fun qrCode(text: String, size: Int): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else WHITE }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** Writes [card] to the cache and opens the system share sheet with the PNG plus [ShareCardContent.shareText]. */
    fun share(context: Context, content: ShareCardContent, card: Bitmap?) {
        val text = content.shareText(context)
        val uri = card?.let { bmp ->
            runCatching {
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "tajik-dictionary.png")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, content.headline)
            putExtra(Intent.EXTRA_TEXT, text)
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
                Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
            }
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser_title))
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun paint(size: Float, color: Int, typeface: Typeface, alpha: Float = 1f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.alpha = (alpha * 255).toInt()
            this.typeface = typeface
        }

    /** Shrinks [p] (down to [minScale]) until [text] fits on one line of [maxWidth]. */
    private fun shrinkToFit(text: String, p: TextPaint, maxWidth: Float, minScale: Float): TextPaint {
        val min = p.textSize * minScale
        while (p.textSize > min && p.measureText(text) > maxWidth) p.textSize -= 2f
        return p
    }

    /** Wraps [text] into at most [maxLines], shrinking the font (down to [minScale]) before ellipsizing. */
    private fun fittedLayout(text: String, p: TextPaint, width: Float, maxLines: Int, minScale: Float): StaticLayout {
        val min = p.textSize * minScale
        while (true) {
            val probe = layout(text, p, width.toInt(), Int.MAX_VALUE)
            if (probe.lineCount <= maxLines || p.textSize <= min) break
            p.textSize -= 3f
        }
        return layout(text, p, width.toInt(), maxLines)
    }

    private fun layout(text: String, p: TextPaint, width: Int, maxLines: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
}
