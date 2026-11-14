package tj.tajikdictionary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.data.settings.ThemeSetting
import tj.tajikdictionary.ui.TajikDictionaryRoot
import tj.tajikdictionary.ui.theme.TajikDictionaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TajikDictionaryApp).container
        val initialQuery = extractQuery(intent)

        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle(initialValue = ThemeSetting.SYSTEM)
            TajikDictionaryTheme(themeSetting = theme) {
                TajikDictionaryRoot(container = container, initialQuery = initialQuery)
            }
        }
    }

    private fun extractQuery(intent: Intent?): String = when (intent?.action) {
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        Intent.ACTION_SEND ->
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        else -> ""
    }
}
