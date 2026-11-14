package tj.tajikdictionary.data.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.io.File

/**
 * Opens the read-only dictionary that ships in `assets/dictionary.sqlite`.
 *
 * On first launch (and after every app update) the asset is copied into internal storage,
 * because SQLite cannot open files directly from the APK. User data never lives here,
 * so the file can be replaced wholesale on every update.
 */
class DictionaryDatabase(private val context: Context) {

    @Volatile private var database: SQLiteDatabase? = null

    /** Must be called off the main thread (copies a file on first use). */
    fun get(): SQLiteDatabase =
        database ?: synchronized(this) {
            database ?: open().also { database = it }
        }

    private fun open(): SQLiteDatabase {
        val target = context.getDatabasePath(FILE_NAME)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installStamp = packageUpdateTime()
        if (!target.exists() || prefs.getLong(KEY_STAMP, -1L) != installStamp) {
            copyAsset(target)
            prefs.edit().putLong(KEY_STAMP, installStamp).apply()
        }
        return SQLiteDatabase.openDatabase(
            target.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
    }

    private fun copyAsset(target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        context.assets.open(FILE_NAME).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Could not install $FILE_NAME" }
        }
    }

    private fun packageUpdateTime(): Long {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return info.lastUpdateTime
    }

    private companion object {
        const val FILE_NAME = "dictionary.sqlite"
        const val PREFS = "dictionary_db"
        const val KEY_STAMP = "installed_for_update_time"
    }
}
