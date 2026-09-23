package tw.app.taiwanweather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tw.app.taiwanweather.data.LoadState
import tw.app.taiwanweather.data.DisplayMode
import tw.app.taiwanweather.data.GeoPoint
import tw.app.taiwanweather.data.Place
import tw.app.taiwanweather.data.SecureStore
import tw.app.taiwanweather.data.TaiwanCounties
import tw.app.taiwanweather.data.WeatherRepository
import tw.app.taiwanweather.location.TaiwanLocationResolver

sealed interface ApiTestState {
    data object Idle : ApiTestState
    data object Testing : ApiTestState
    data object Available : ApiTestState
    data class Failed(val message: String) : ApiTestState
}

data class AppUiState(
    val loadState: LoadState = LoadState.Idle,
    val selected: Place = Place("臺北市", "中正區"),
    val favorites: List<Place> = emptyList(),
    val townships: List<String> = emptyList(),
    val cwaKey: String = "",
    val moenvKey: String = "",
    val message: String? = null,
    val cwaTestState: ApiTestState = ApiTestState.Idle,
    val moenvTestState: ApiTestState = ApiTestState.Idle,
    val displayMode: DisplayMode = DisplayMode.SYSTEM,
    val loadingTownships: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureStore(application)
    private val repository = WeatherRepository.create(application)
    private val locationResolver = TaiwanLocationResolver(application)
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()
    private var refreshJob: Job? = null
    private var selectedCoordinate: GeoPoint? = null

    init {
        viewModelScope.launch {
            val keys = store.apiKeys()
            val selected = store.selected() ?: _ui.value.selected
            _ui.value = _ui.value.copy(
                selected = selected,
                favorites = store.favorites(),
                cwaKey = keys.first,
                moenvKey = keys.second,
                displayMode = store.displayMode()
            )
            if (keys.first.isNotBlank()) {
                selectedCoordinate = locationResolver.coordinate(selected)
                refresh(forceRefresh = false)
            }
        }
    }

    fun refresh(forceRefresh: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
        val state = _ui.value
        val oldReport = (state.loadState as? LoadState.Success)?.report
        _ui.value = state.copy(
            loadState = if (oldReport == null) LoadState.Loading else state.loadState,
            isRefreshing = true,
            refreshError = null,
            message = null
        )
        try {
            val report = repository.report(
                state.selected,
                tw.app.taiwanweather.data.ApiKeys(state.cwaKey, state.moenvKey),
                selectedCoordinate,
                forceRefresh
            )
            if (_ui.value.selected == state.selected) {
                _ui.value = _ui.value.copy(loadState = LoadState.Success(report), isRefreshing = false)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = WeatherRepository.friendlyError(error)
            _ui.value = _ui.value.copy(
                loadState = oldReport?.let(LoadState::Success) ?: LoadState.Error(message),
                isRefreshing = false,
                refreshError = if (oldReport != null) message else null
            )
        }
        }
    }

    fun loadTownships(county: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(townships = emptyList(), loadingTownships = true, message = null)
        runCatching { repository.townships(county, _ui.value.cwaKey) }
            .onSuccess { _ui.value = _ui.value.copy(townships = it, loadingTownships = false, message = null) }
            .onFailure { _ui.value = _ui.value.copy(loadingTownships = false, message = WeatherRepository.friendlyError(it)) }
    }

    fun select(place: Place) = viewModelScope.launch {
        if (TaiwanCounties.none { it.name == place.county }) {
            _ui.value = _ui.value.copy(message = "目前不支援此地點天氣")
            return@launch
        }
        _ui.value = _ui.value.copy(selected = place, townships = emptyList())
        selectedCoordinate = locationResolver.coordinate(place)
        store.saveSelected(place)
        refresh(forceRefresh = false)
    }

    fun toggleFavorite(place: Place = _ui.value.selected) = viewModelScope.launch {
        val state = _ui.value
        val updated = if (place in state.favorites) {
            state.favorites - place
        } else {
            state.favorites + place
        }
        store.saveFavorites(updated)
        _ui.value = state.copy(favorites = updated.distinct())
    }

    fun removeFavorite(place: Place) = viewModelScope.launch {
        val updated = _ui.value.favorites - place
        store.saveFavorites(updated)
        _ui.value = _ui.value.copy(favorites = updated)
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        val favorites = _ui.value.favorites.toMutableList()
        if (fromIndex !in favorites.indices || toIndex !in favorites.indices) return@launch
        val place = favorites.removeAt(fromIndex)
        favorites.add(toIndex, place)
        store.saveFavorites(favorites)
        _ui.value = _ui.value.copy(favorites = favorites)
    }

    fun saveKeys(cwa: String, moenv: String) = viewModelScope.launch {
        store.saveApiKeys(cwa, moenv)
        _ui.value = _ui.value.copy(cwaKey = cwa.trim(), moenvKey = moenv.trim(), message = "設定已安全儲存")
    }

    fun testCwa(key: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(cwaTestState = ApiTestState.Testing, message = null)
        val result = runCatching { repository.testCwa(key.trim()) }
            .fold({ ApiTestState.Available }, { ApiTestState.Failed(WeatherRepository.friendlyError(it)) })
        _ui.value = _ui.value.copy(cwaTestState = result)
    }

    fun testMoenv(key: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(moenvTestState = ApiTestState.Testing, message = null)
        val result = runCatching { repository.testMoenv(key.trim()) }
            .fold({ ApiTestState.Available }, { ApiTestState.Failed(WeatherRepository.friendlyError(it)) })
        _ui.value = _ui.value.copy(moenvTestState = result)
    }

    fun resetCwaTest() { _ui.value = _ui.value.copy(cwaTestState = ApiTestState.Idle) }

    fun resetMoenvTest() { _ui.value = _ui.value.copy(moenvTestState = ApiTestState.Idle) }

    fun setDisplayMode(mode: DisplayMode) = viewModelScope.launch {
        _ui.value = _ui.value.copy(displayMode = mode)
        store.saveDisplayMode(mode)
    }

    fun locate() = viewModelScope.launch {
        _ui.value = _ui.value.copy(message = "正在尋找你的位置…")
        locationResolver.currentPlace()
            .onSuccess {
                selectedCoordinate = it.coordinate
                selectResolved(it.place)
            }
            .onFailure { _ui.value = _ui.value.copy(message = WeatherRepository.friendlyError(it)) }
    }

    private fun selectResolved(place: Place) = viewModelScope.launch {
        _ui.value = _ui.value.copy(selected = place, townships = emptyList())
        store.saveSelected(place)
        refresh(forceRefresh = false)
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }
}
