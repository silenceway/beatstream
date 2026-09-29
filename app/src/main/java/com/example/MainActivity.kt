package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.MusicPlaybackService
import com.example.ui.MainViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.SleepTimerDialog
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.YTRed
import com.example.ui.theme.YTSurface
import com.example.ui.theme.YTSurfaceElevated

class MainActivity : ComponentActivity() {

    private var playbackService: MusicPlaybackService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicPlaybackService.MusicBinder
            playbackService = binder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start playback service
        MusicPlaybackService.start(this)
        val serviceIntent = Intent(this, MusicPlaybackService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()

                // Attach player controller once service binds
                DisposableEffect(isBound) {
                    if (isBound) {
                        playbackService?.attachPlayer(viewModel.playerController)
                    }
                    onDispose { }
                }

                BeatStreamApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatStreamApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val historyTracks by viewModel.historyTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    // Handle back button for sub-sheets and tabs
    BackHandler(enabled = uiState.showFullPlayer || uiState.selectedTab != 0) {
        if (uiState.showFullPlayer) {
            viewModel.closeFullPlayer()
        } else if (uiState.selectedTab != 0) {
            viewModel.setSelectedTab(0)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                            color = YTRed
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BeatStream",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = " Music",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Normal,
                                color = YTRed
                            )
                        )
                    }
                },
                actions = {
                    // Equalizer Action
                    IconButton(
                        onClick = { viewModel.showEqualizer(true) },
                        modifier = Modifier.size(48.dp).testTag("top_bar_equalizer_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Equalizer",
                            tint = if (playerState.equalizerEnabled) YTRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Sleep Timer Action
                    IconButton(
                        onClick = { viewModel.showSleepTimer(true) },
                        modifier = Modifier.size(48.dp).testTag("top_bar_timer_btn")
                    ) {
                        if (playerState.sleepTimerMinutesLeft != null) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = YTRed) {
                                        Text("${playerState.sleepTimerMinutesLeft}m")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep timer active",
                                    tint = YTRed
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Sleep timer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(YTSurface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Mini Player (Only visible when a track is chosen and full player isn't open)
                AnimatedVisibility(
                    visible = playerState.currentTrack != null && !uiState.showFullPlayer,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    MiniPlayer(
                        playerState = playerState,
                        onOpenFullPlayer = { viewModel.openFullPlayer() },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() }
                    )
                }

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = YTSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(64.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "Home", Pair(Icons.Filled.Home, Icons.Outlined.Home)),
                        Triple(1, "Explore", Pair(Icons.Filled.Explore, Icons.Outlined.Explore)),
                        Triple(2, "Library", Pair(Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)),
                        Triple(3, "Search", Pair(Icons.Filled.Search, Icons.Outlined.Search))
                    )

                    tabs.forEach { (index, label, icons) ->
                        val selected = uiState.selectedTab == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.setSelectedTab(index) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) icons.first else icons.second,
                                    contentDescription = label
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = YTRed,
                                selectedTextColor = YTRed,
                                indicatorColor = YTRed.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_tab_$label")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                0 -> HomeScreen(
                    playerState = playerState,
                    featuredTracks = viewModel.featuredTracks,
                    quickPicks = viewModel.quickPicks,
                    historyTracks = historyTracks,
                    moodCategories = viewModel.moodCategories,
                    isFavorite = { viewModel.isTrackFavorite(it) },
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onAddToPlaylist = { viewModel.showAddToPlaylist(it) },
                    onMoodClick = { mood ->
                        viewModel.selectMoodCategory(mood)
                        viewModel.setSelectedTab(3) // Switch to search with mood
                    }
                )

                1 -> ExploreScreen(
                    playerState = playerState,
                    featuredTracks = viewModel.featuredTracks,
                    moodCategories = viewModel.moodCategories,
                    isFavorite = { viewModel.isTrackFavorite(it) },
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onAddToPlaylist = { viewModel.showAddToPlaylist(it) },
                    onMoodClick = { mood ->
                        viewModel.selectMoodCategory(mood)
                        viewModel.setSelectedTab(3)
                    }
                )

                2 -> LibraryScreen(
                    playerState = playerState,
                    favoriteTracks = favoriteTracks,
                    historyTracks = historyTracks,
                    playlists = playlists,
                    onCreatePlaylistClick = { viewModel.showCreatePlaylist(true) },
                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onAddToPlaylist = { viewModel.showAddToPlaylist(it) }
                )

                3 -> SearchScreen(
                    searchQuery = uiState.searchQuery,
                    selectedGenre = uiState.selectedGenreFilter,
                    searchResults = searchResults,
                    playerState = playerState,
                    isFavorite = { viewModel.isTrackFavorite(it) },
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onGenreSelect = { viewModel.onGenreFilterSelected(it) },
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onAddToPlaylist = { viewModel.showAddToPlaylist(it) }
                )
            }
        }
    }

    // Full Player Modal Bottom Sheet
    if (uiState.showFullPlayer && playerState.currentTrack != null) {
        val current = playerState.currentTrack!!
        FullPlayerSheet(
            playerState = playerState,
            isFavorite = viewModel.isTrackFavorite(current.id),
            onDismiss = { viewModel.closeFullPlayer() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onSkipNext = { viewModel.skipNext() },
            onSkipPrevious = { viewModel.skipPrevious() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onToggleFavorite = { viewModel.toggleFavorite(current) },
            onOpenEqualizer = { viewModel.showEqualizer(true) },
            onOpenSleepTimer = { viewModel.showSleepTimer(true) },
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onAddToPlaylist = { viewModel.showAddToPlaylist(current) },
            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
            onClearQueue = { viewModel.clearQueue() },
            onPlayQueueTrack = { viewModel.playTrack(it) }
        )
    }

    // Equalizer Dialog
    if (uiState.showEqualizerDialog) {
        EqualizerDialog(
            playerState = playerState,
            controller = viewModel.playerController,
            onDismiss = { viewModel.showEqualizer(false) }
        )
    }

    // Sleep Timer Dialog
    if (uiState.showSleepTimerDialog) {
        SleepTimerDialog(
            currentMinutesLeft = playerState.sleepTimerMinutesLeft,
            onSetTimer = { viewModel.setSleepTimer(it) },
            onDismiss = { viewModel.showSleepTimer(false) }
        )
    }

    // Create Playlist Dialog
    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylist(false) },
            onCreate = { name, desc -> viewModel.createPlaylist(name, desc) }
        )
    }

    // Add To Playlist Dialog
    if (uiState.trackForPlaylist != null) {
        AddToPlaylistDialog(
            track = uiState.trackForPlaylist!!,
            playlists = playlists,
            onDismiss = { viewModel.showAddToPlaylist(null) },
            onSelectPlaylist = { plId ->
                viewModel.addTrackToPlaylist(plId, uiState.trackForPlaylist!!)
            },
            onCreateNewClick = {
                viewModel.showCreatePlaylist(true)
            }
        )
    }
}
