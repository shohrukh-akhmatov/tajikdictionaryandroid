package tj.tajikdictionary.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.HistoryEntity
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Entry
import tj.tajikdictionary.domain.model.SearchOutcome

data class WordOfTheDayUi(val entry: Entry, val preview: String)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val dictionary: DictionaryRepository,
    private val userData: UserDataRepository,
    private val settings: SettingsRepository,
    initialQuery: String = "",
) : ViewModel() {

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query

    val mode: StateFlow<DictionaryMode> = settings.mode
        .stateIn(viewModelScope, SharingStarted.Eagerly, DictionaryMode.TJ_RU)

    val recent: StateFlow<List<HistoryEntity>> = userData.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wordOfTheDay: StateFlow<WordOfTheDayUi?> = mode.flatMapLatest { currentMode ->
        flow {
            val entry = dictionary.wordOfTheDay()
            if (entry == null) {
                emit(null)
            } else {
                val preview = dictionary.preview(entry.id, currentMode)
                emit(WordOfTheDayUi(entry, preview))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val outcome: StateFlow<SearchOutcome> = combine(
        _query.debounce { if (it.isBlank()) 0L else 150L },
        mode,
    ) { q, m -> q to m }
        .flatMapLatest { (q, m) ->
            flow {
                if (q.isBlank()) emit(SearchOutcome.Empty)
                else emit(dictionary.search(q, m))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchOutcome.Empty)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun appendLetter(ch: Char) {
        _query.value += ch
    }

    fun onModeChange(newMode: DictionaryMode) {
        viewModelScope.launch { settings.setMode(newMode) }
    }

    fun clearHistory() {
        viewModelScope.launch { userData.clearHistory() }
    }

    class Factory(
        private val dictionary: DictionaryRepository,
        private val userData: UserDataRepository,
        private val settings: SettingsRepository,
        private val initialQuery: String = "",
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(dictionary, userData, settings, initialQuery) as T
    }
}
