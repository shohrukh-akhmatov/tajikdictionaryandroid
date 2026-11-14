package tj.tajikdictionary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import tj.tajikdictionary.data.settings.ThemeSetting
import tj.tajikdictionary.ui.LaunchRequest
import tj.tajikdictionary.ui.TajikDictionaryRoot
import tj.tajikdictionary.ui.theme.TajikDictionaryTheme
import tj.tajikdictionary.widget.WordOfTheDayWidget

class MainActivity : ComponentActivity() {
    private val launchRequest = MutableStateFlow(LaunchRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TajikDictionaryApp).container
        launchRequest.value = extractLaunchRequest(intent, seq = 1L)

        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeSetting.SYSTEM)
            val request by launchRequest.collectAsStateWithLifecycle()
            TajikDictionaryTheme(themeSetting = theme) {
                TajikDictionaryRoot(container = container, launchRequest = request)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val nextSeq = launchRequest.value.seq + 1L
        launchRequest.value = extractLaunchRequest(intent, seq = nextSeq)
    }

    private fun extractLaunchRequest(intent: Intent?, seq: Long): LaunchRequest {
        val widgetEntryId = intent?.getLongExtra(WordOfTheDayWidget.EXTRA_ENTRY_ID, -1L)
            ?.takeIf { it > 0L }
        val query = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
            Intent.ACTION_SEND ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            else -> ""
        }
        return LaunchRequest(query = query, entryId = widgetEntryId, seq = seq)
    }
}
