package tj.tajikdictionary.ui.share

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tj.tajikdictionary.R
import tj.tajikdictionary.domain.model.GamificationSummary
import tj.tajikdictionary.domain.model.PlacementResult
import java.util.Locale
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color as ComposeColor

// MARK: card builders (same cards, stats and order as iOS `ShareCardContent`)

object ShareCardsContent {
    fun round(context: Context, xp: Int, accuracy: Int, newWords: Int, streakDays: Int, cefr: String): ShareCardContent {
        val stats = buildList {
            add(ShareStat("+$xp", "XP"))
            add(ShareStat("$accuracy%", context.getString(R.string.share_stat_accuracy)))
            if (newWords > 0) add(ShareStat("$newWords", context.getString(R.string.share_stat_new_words)))
            if (streakDays > 0) add(ShareStat("$streakDays", context.getString(R.string.share_stat_streak)))
        }
        return ShareCardContent(
            symbol = Icons.Filled.EmojiEvents,
            headline = context.getString(R.string.share_headline_round),
            subtitle = context.getString(R.string.share_headline_level, cefr),
            stats = stats,
        )
    }

    fun placement(context: Context, result: PlacementResult, levelTitle: String) = ShareCardContent(
        symbol = Icons.Filled.School,
        headline = context.getString(R.string.share_headline_level, result.cefrLevel.code),
        subtitle = levelTitle,
        stats = listOf(
            ShareStat("~${result.estimatedKnownWords}", context.getString(R.string.share_stat_known_words)),
            ShareStat(
                String.format(Locale.US, "%.0f%%", result.textRecognitionPercent),
                context.getString(R.string.share_stat_recognition),
            ),
            ShareStat("${result.correctCount}/${result.totalCount}", context.getString(R.string.share_stat_score)),
        ),
    )

    fun progress(context: Context, summary: GamificationSummary, levelTitle: String) = ShareCardContent(
        symbol = Icons.Filled.LocalFireDepartment,
        headline = if (summary.currentStreakDays > 0) {
            context.getString(R.string.share_headline_streak, summary.currentStreakDays)
        } else {
            context.getString(R.string.share_headline_progress)
        },
        subtitle = context.getString(R.string.share_subtitle_level, summary.level, levelTitle),
        stats = listOf(
            ShareStat("${summary.totalXp}", "XP"),
            ShareStat("${summary.masteredCount}", context.getString(R.string.share_stat_mastered)),
            ShareStat("~${summary.estimatedKnownWords}", context.getString(R.string.share_stat_known_words)),
            ShareStat(summary.cefrLevel.code, context.getString(R.string.share_stat_level)),
        ),
    )

    fun badge(context: Context, icon: ImageVector, title: String, subtitle: String, summary: GamificationSummary) =
        ShareCardContent(
            symbol = icon,
            headline = context.getString(R.string.share_headline_badge),
            subtitle = "$title — $subtitle",
            stats = listOf(
                ShareStat("${summary.totalXp}", "XP"),
                ShareStat("${summary.longestStreakDays}", context.getString(R.string.share_stat_best_streak)),
            ),
        )
}

// MARK: buttons + preview dialog (the card is rendered only after a tap, never in lists)

/** Full-width "Share result" button; [content] is built when tapped. */
@Composable
fun ShareCardButton(content: (Context) -> ShareCardContent, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var presented by remember { mutableStateOf<ShareCardContent?>(null) }
    OutlinedButton(onClick = { presented = content(context) }, modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.share_action))
    }
    presented?.let { ShareCardDialog(it, onDismiss = { presented = null }) }
}

/** Small share icon (streak card, unlocked achievements). */
@Composable
fun ShareCardIconButton(content: (Context) -> ShareCardContent, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var presented by remember { mutableStateOf<ShareCardContent?>(null) }
    IconButton(onClick = { presented = content(context) }, modifier = modifier) {
        Icon(
            Icons.Filled.Share,
            contentDescription = stringResource(R.string.share_button),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
    presented?.let { ShareCardDialog(it, onDismiss = { presented = null }) }
}

/** Preview of the rendered card plus a Share button that sends PNG + text (both carry the app link). */
@Composable
fun ShareCardDialog(content: ShareCardContent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val symbolPainter = rememberVectorPainter(content.symbol)
    val brandPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.MenuBook)
    var card by remember(content) { mutableStateOf<Bitmap?>(null) }
    val cta = stringResource(R.string.share_cta)

    LaunchedEffect(content) {
        val symbol = symbolPainter.rasterize(120, ComposeColor(0xFFFFCC00))
        val brand = brandPainter.rasterize(45, ComposeColor(0xFF04392A))
        card = withContext(Dispatchers.Default) {
            runCatching { ShareCards.render(content, cta, symbol, brand) }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.share_close)) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ShareCards.WIDTH / ShareCards.HEIGHT.toFloat())
                        .clip(RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = card
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = content.shareText(context),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
                Button(
                    onClick = { ShareCards.share(context, content, card) },
                    enabled = card != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_button))
                }
            }
        }
    }
}

private fun Painter.rasterize(px: Int, tint: ComposeColor): Bitmap {
    val image = ImageBitmap(px, px)
    val painter = this
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(image), Size(px.toFloat(), px.toFloat())) {
        with(painter) { draw(size, colorFilter = ColorFilter.tint(tint)) }
    }
    return image.asAndroidBitmap()
}
