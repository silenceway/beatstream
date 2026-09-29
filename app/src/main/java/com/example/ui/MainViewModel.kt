package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.data.local.AppDatabase
import com.example.model.MoodCategory
import com.example.model.Playlist
import com.example.model.Track
import com.example.player.PlayerController
import com.example.player.PlayerState
import com.example.player.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val selectedTab: Int = 0, // 0: Home, 1: Explore, 2: Library, 3: Search
    val showFullPlayer: Boolean = false,
    val showEqualizerDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val trackForPlaylist: Track? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val searchQuery: String = "",
    val selectedGenreFilter: String = "All",
    val selectedMood: MoodCategory? = null,
    val selectedPlaylist: Playlist? = null,
    val isSearching: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(AppDatabase.getDatabase(application))
    val playerController = PlayerController(application)

    val playerState: StateFlow<PlayerState> = playerController.state

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val favoriteTracks: StateFlow<List<Track>> = repository.favoriteTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyTracks: StateFlow<List<Track>> = repository.historyTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val featuredTracks: List<Track> = repository.getFeaturedTracks()
    val quickPicks: List<Track> = repository.getQuickPicks()
    val moodCategories: List<MoodCategory> = repository.getMoodCategories()

    private val _searchResults = MutableStateFlow(repository.getFeaturedTracks())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    init {
        // Automatically attach history logging on track change
        playerController.onTrackChangedListener = { track ->
            viewModelScope.launch {
                repository.recordPlay(track)
            }
        }
    }

    fun setSelectedTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun openFullPlayer() {
        _uiState.update { it.copy(showFullPlayer = true) }
    }

    fun closeFullPlayer() {
        _uiState.update { it.copy(showFullPlayer = false) }
    }

    fun showEqualizer(show: Boolean) {
        _uiState.update { it.copy(showEqualizerDialog = show) }
    }

    fun showSleepTimer(show: Boolean) {
        _uiState.update { it.copy(showSleepTimerDialog = show) }
    }

    fun showAddToPlaylist(track: Track?) {
        _uiState.update { it.copy(trackForPlaylist = track) }
    }

    fun showCreatePlaylist(show: Boolean) {
        _uiState.update { it.copy(showCreatePlaylistDialog = show) }
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        playerController.playTrack(track, queue)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun skipNext() {
        playerController.skipNext()
    }

    fun skipPrevious() {
        playerController.skipPrevious()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun toggleRepeat() {
        playerController.toggleRepeat()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int?) {
        playerController.setSleepTimer(minutes)
    }

    fun addToQueue(track: Track) {
        playerController.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerController.removeFromQueue(index)
    }

    fun clearQueue() {
        playerController.clearQueue()
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    fun isTrackFavorite(trackId: String): Boolean {
        return favoriteTracks.value.any { it.id == trackId }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        performSearch(query, _uiState.value.selectedGenreFilter)
    }

    fun onGenreFilterSelected(genre: String) {
        _uiState.update { it.copy(selectedGenreFilter = genre) }
        performSearch(_uiState.value.searchQuery, genre)
    }

    fun selectMoodCategory(mood: MoodCategory?) {
        _uiState.update { it.copy(selectedMood = mood) }
        if (mood != null) {
            val moodFilter = when (mood.id) {
                "chill" -> "Lofi & Chill"
                "workout" -> "Rock"
                "party" -> "Pop"
                "romance" -> "Pop"
                else -> "Pop"
            }
            performSearch("", moodFilter)
        }
    }

    private fun performSearch(query: String, genre: String) {
        val results = repository.searchTracks(query, genre)
        _searchResults.value = results
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, description)
            _uiState.update { it.copy(showCreatePlaylistDialog = false) }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track)
            _uiState.update { it.copy(trackForPlaylist = null) }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _uiState.update { it.copy(selectedPlaylist = playlist) }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
