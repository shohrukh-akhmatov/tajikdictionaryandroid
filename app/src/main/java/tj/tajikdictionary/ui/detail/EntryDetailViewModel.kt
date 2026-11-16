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
import tj.tajikdictionary.data.settings.FontScaleSetting
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.FlashcardEntity
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.EntryDetail

data class EntryDetailUiState(
    val detail: EntryDetail? = null,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val cardProgress: FlashcardEntity? = null,
    val mode: DictionaryMode = DictionaryMode.TJ_RU,
    val speechRate: Float = 1.0f,
    val fontScale: FontScaleSetting = FontScaleSetting.MEDIUM,
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
        userData.flashcard(entryId),
        combine(settings.mode, settings.speechRate, settings.fontScale, ::Triple),
    ) { (detail, loading), fav, card, (mode, rate, scale) ->
        EntryDetailUiState(
            detail = detail,
            isLoading = loading,
            isFavorite = fav,
            cardProgress = card,
            mode = mode,
            speechRate = rate,
            fontScale = scale,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailUiState())

    init {
        viewModelScope.launch {
            val detail = dictionary.detail(entryId)
            loaded.value = detail to false
            if (detail != null) {
                val mode = settings.mode.first()
                userData.recordView(
                    entryId = detail.entry.id,
                    language = detail.entry.language,
                    headword = detail.entry.headword,
                    preview = previewFor(detail, mode),
                )
            }
        }
    }

    fun toggleFavorite() {
        val state = uiState.value
        val detail = state.detail ?: return
        viewModelScope.launch {
            userData.setFavorite(
                entryId = detail.entry.id,
                language = detail.entry.language,
                headword = detail.entry.headword,
                preview = previewFor(detail, state.mode),
                favorite = !state.isFavorite,
            )
        }
    }

    fun queueForPractice() {
        val state = uiState.value
        val detail = state.detail ?: return
        viewModelScope.launch {
            userData.queueWordForPractice(
                entryId = detail.entry.id,
                language = detail.entry.language,
                headword = detail.entry.headword,
                preview = previewFor(detail, state.mode),
            )
        }
    }

    fun markAsKnown() {
        val state = uiState.value
        val detail = state.detail ?: return
        viewModelScope.launch {
            userData.markEntryKnown(
                entryId = detail.entry.id,
                language = detail.entry.language,
                headword = detail.entry.headword,
                preview = previewFor(detail, state.mode),
            )
        }
    }

    private fun previewFor(detail: EntryDetail, mode: DictionaryMode): String {
        val targetLang = mode.translationTarget(detail.entry.language)
        return detail.translations[targetLang].orEmpty()
            .take(4)
            .joinToString(", ") { it.headword }
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
