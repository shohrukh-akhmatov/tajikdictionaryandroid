package tj.tajikdictionary.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tj.tajikdictionary.MainActivity
import tj.tajikdictionary.R
import tj.tajikdictionary.TajikDictionaryApp

/** Home screen widget displaying the offline deterministic Word of the Day. */
class WordOfTheDayWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as TajikDictionaryApp
                val mode = app.container.settings.mode.first()
                val entry = app.container.dictionary.wordOfTheDay()
                val preview = entry?.let { app.container.dictionary.preview(it.id, mode) }.orEmpty()

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_word_of_the_day).apply {
                        setTextViewText(R.id.widget_label, context.getString(R.string.word_of_the_day))
                        setTextViewText(R.id.widget_lang, entry?.language?.tag ?: "TJ")
                        setTextViewText(R.id.widget_headword, entry?.headword ?: context.getString(R.string.app_name))
                        setTextViewText(R.id.widget_preview, preview.ifBlank { context.getString(R.string.widget_description) })

                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            if (entry != null) putExtra(EXTRA_ENTRY_ID, entry.id)
                        }
                        val pi = PendingIntent.getActivity(
                            context,
                            widgetId,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setOnClickPendingIntent(R.id.widget_root, pi)
                    }
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "tj.tajikdictionary.EXTRA_ENTRY_ID"
    }
}
