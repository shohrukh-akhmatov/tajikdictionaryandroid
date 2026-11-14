package tj.tajikdictionary.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.EntryDetail

data class EntryDetailUiState(
    val detail: EntryDetail? = null,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val mode: DictionaryMode = DictionaryMode.TJ_RU,
)

class EntryDetailViewModel(
    private val entryId: Long,
    private val dictionary: DictionaryRepository,
    private val userData: UserDataRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val loaded = MutableStateFlow<Pair<EntryDetail?, Boolean>>(null to true)

    val uiState: StateFlow<EntryDetailUiState> = combine(
        loaded,
        userData.isFavorite(entryId),
        settings.mode,
    ) { (detail, loading), fav, mode ->
        EntryDetailUiState(detail = detail, isLoading = loading, isFavorite = fav, mode = mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailUiState())

    init {
        viewModelScope.launch {
            val detail = dictionary.detail(entryId)
            loaded.value = detail to false
            if (detail != null) {
                val mode = settings.mode.first()
                val targetLang = mode.translationTarget(detail.entry.language)
                val preview = detail.translations[targetLang].orEmpty()
                    .take(4)
                    .joinToString(", ") { it.headword }
                userData.recordView(
                    entryId = detail.entry.id,
                    language = detail.entry.language,
                    headword = detail.entry.headword,
                    preview = preview,
                )
            }
        }
    }

    fun toggleFavorite() {
        val state = uiState.value
        val detail = state.detail ?: return
        val targetLang = state.mode.translationTarget(detail.entry.language)
        val preview = detail.translations[targetLang].orEmpty()
            .take(4)
            .joinToString(", ") { it.headword }
        viewModelScope.launch {
            userData.setFavorite(
                entryId = detail.entry.id,
                language = detail.entry.language,
                headword = detail.entry.headword,
                preview = preview,
                favorite = !state.isFavorite,
            )
        }
    }

    class Factory(
        private val entryId: Long,
        private val dictionary: DictionaryRepository,
        private val userData: UserDataRepository,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EntryDetailViewModel(entryId, dictionary, userData, settings) as T
    }
}
