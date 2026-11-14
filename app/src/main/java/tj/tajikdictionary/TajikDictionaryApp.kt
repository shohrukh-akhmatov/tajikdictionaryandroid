package tj.tajikdictionary

import android.app.Application
import android.content.Context
import tj.tajikdictionary.data.dictionary.DictionaryDatabase
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.data.user.UserDatabase

/** Manual dependency container — simple enough for the MVP; swap for Hilt/Koin if it grows. */
class AppContainer(context: Context) {
    val dictionary: DictionaryRepository by lazy { DictionaryRepository(DictionaryDatabase(context)) }
    val userData: UserDataRepository by lazy { UserDataRepository(UserDatabase.create(context)) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
}

class TajikDictionaryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
