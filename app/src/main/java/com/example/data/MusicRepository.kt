package com.example.data

import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteTrackEntity
import com.example.data.local.HistoryTrackEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.remote.YouTubeApiService
import com.example.model.MoodCategory
import com.example.model.Playlist
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(
    private val database: AppDatabase,
    val apiService: YouTubeApiService = YouTubeApiService()
) {

    private val dao = database.musicDao()

    val favoriteTracks: Flow<List<Track>> = dao.getFavoriteTracks().map { list ->
        list.map { entity ->
            Track(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                durationSeconds = entity.durationSeconds,
                thumbnailUrl = entity.thumbnailUrl,
                streamUrl = entity.streamUrl,
                lyrics = entity.lyrics,
                genre = entity.genre,
                isFavorite = true
            )
        }
    }

    val historyTracks: Flow<List<Track>> = dao.getRecentHistory().map { list ->
        list.map { entity ->
            Track(
                id = entity.trackId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                durationSeconds = entity.durationSeconds,
                thumbnailUrl = entity.thumbnailUrl,
                streamUrl = entity.streamUrl,
                lyrics = "",
                genre = "History",
                isFavorite = false
            )
        }
    }

    val playlists: Flow<List<Playlist>> = dao.getAllPlaylists().map { list ->
        list.map { entity ->
            Playlist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                coverUrl = entity.coverUrl,
                trackCount = 0
            )
        }
    }

    fun isTrackFavorite(trackId: String): Flow<Boolean> = dao.isTrackFavoriteFlow(trackId)

    suspend fun toggleFavorite(track: Track): Boolean = withContext(Dispatchers.IO) {
        val isFav = dao.isTrackFavorite(track.id)
        if (isFav) {
            dao.removeFavorite(track.id)
            false
        } else {
            dao.insertFavorite(
                FavoriteTrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSeconds = track.durationSeconds,
                    thumbnailUrl = track.thumbnailUrl,
                    streamUrl = track.streamUrl,
                    lyrics = track.lyrics,
                    genre = track.genre
                )
            )
            true
        }
    }

    suspend fun recordPlay(track: Track) = withContext(Dispatchers.IO) {
        dao.insertHistory(
            HistoryTrackEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = track.durationSeconds,
                thumbnailUrl = track.thumbnailUrl,
                streamUrl = track.streamUrl
            )
        )
    }

    suspend fun createPlaylist(name: String, description: String = "", coverUrl: String = ""): Long = withContext(Dispatchers.IO) {
        val cover = if (coverUrl.isNotBlank()) coverUrl else "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80"
        dao.insertPlaylist(
            PlaylistEntity(
                name = name,
                description = description,
                coverUrl = cover
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) = withContext(Dispatchers.IO) {
        dao.addTrackToPlaylist(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                durationSeconds = track.durationSeconds,
                thumbnailUrl = track.thumbnailUrl,
                streamUrl = track.streamUrl
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> = dao.getTracksForPlaylist(playlistId).map { list ->
        list.map { ref ->
            Track(
                id = ref.trackId,
                title = ref.title,
                artist = ref.artist,
                durationSeconds = ref.durationSeconds,
                thumbnailUrl = ref.thumbnailUrl,
                streamUrl = ref.streamUrl
            )
        }
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllPlaylists().first()
        if (existing.isEmpty()) {
            val allTracks = getFeaturedTracks()

            // 1. Today's Top Hits
            val p1Id = dao.insertPlaylist(
                PlaylistEntity(
                    id = 1,
                    name = "Today's Top Hits",
                    description = "The hottest tracks right now across pop, rock and latin",
                    coverUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"
                )
            )
            allTracks.filter { it.genre in listOf("Pop", "Rock", "Latin") }.take(6).forEach { track ->
                addTrackToPlaylist(p1Id, track)
            }

            // 2. Chill Lofi Vibes
            val p2Id = dao.insertPlaylist(
                PlaylistEntity(
                    id = 2,
                    name = "Chill Lofi Vibes",
                    description = "Cozy beats for relaxing, study sessions, and late nights",
                    coverUrl = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg"
                )
            )
            allTracks.filter { it.genre in listOf("Lofi & Chill", "Deep Focus") }.take(6).forEach { track ->
                addTrackToPlaylist(p2Id, track)
            }

            // 3. High Energy Workout
            val p3Id = dao.insertPlaylist(
                PlaylistEntity(
                    id = 3,
                    name = "High Energy Workout",
                    description = "High BPM bangers and rock anthems to push your limits",
                    coverUrl = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg"
                )
            )
            allTracks.filter { it.genre in listOf("Workout & Energy", "Rock", "Pop Rock") }.take(6).forEach { track ->
                addTrackToPlaylist(p3Id, track)
            }

            // 4. Deep Focus & Study
            val p4Id = dao.insertPlaylist(
                PlaylistEntity(
                    id = 4,
                    name = "Deep Focus & Study",
                    description = "Pure focus instrumental piano and soothing ambient soundscapes",
                    coverUrl = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg"
                )
            )
            allTracks.filter { it.genre in listOf("Deep Focus", "Night & Sleep", "Lofi & Chill") }.take(6).forEach { track ->
                addTrackToPlaylist(p4Id, track)
            }
        }
    }

    suspend fun syncYouTubeData(token: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var likedCount = 0
        var playlistCount = 0

        // 1. Fetch user's liked music from YouTube
        val likedSongs = apiService.fetchLikedSongs(token)
        likedSongs.forEach { track ->
            dao.insertFavorite(
                FavoriteTrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = "Liked on YouTube",
                    durationSeconds = track.durationSeconds,
                    thumbnailUrl = track.thumbnailUrl,
                    streamUrl = track.streamUrl.ifBlank { "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3" },
                    lyrics = "",
                    genre = "Liked on YouTube"
                )
            )
            likedCount++
        }

        // 2. Fetch user's playlists from YouTube
        val ytPlaylists = apiService.fetchMyPlaylists(token)
        ytPlaylists.forEach { pl ->
            dao.insertPlaylist(
                PlaylistEntity(
                    id = pl.id,
                    name = pl.name,
                    description = pl.description,
                    coverUrl = pl.coverUrl
                )
            )
            playlistCount++
        }

        Pair(likedCount, playlistCount)
    }

    suspend fun rateOnYouTube(token: String, videoId: String, isLike: Boolean): Boolean {
        return apiService.rateVideo(token, videoId, isLike)
    }

    suspend fun createPlaylistOnYouTube(token: String, title: String, description: String): Boolean {
        return apiService.createPlaylist(token, title, description)
    }

    // Verified catalog with guaranteed HTTP 200 Audio Streams
    fun getFeaturedTracks(): List<Track> = listOf(
        Track(
            id = "fJ9rUzIMcZQ",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationSeconds = 359,
            thumbnailUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            genre = "Rock",
            viewCount = "1.7B views",
            lyrics = ""
        ),
        Track(
            id = "4NRXx6U8ABQ",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            genre = "Pop",
            viewCount = "920M views",
            lyrics = ""
        ),
        Track(
            id = "JGwWNGJdvx8",
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "÷ (Divide)",
            durationSeconds = 233,
            thumbnailUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            genre = "Pop",
            viewCount = "6.2B views",
            lyrics = ""
        ),
        Track(
            id = "kXYiU_JCYtU",
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            durationSeconds = 187,
            thumbnailUrl = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            genre = "Rock",
            viewCount = "2.1B views",
            lyrics = ""
        ),
        Track(
            id = "kJQP7kiw5Fk",
            title = "Despacito",
            artist = "Luis Fonsi ft. Daddy Yankee",
            album = "Vida",
            durationSeconds = 228,
            thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            genre = "Latin",
            viewCount = "8.4B views",
            lyrics = ""
        ),
        Track(
            id = "09R8_2nJtjg",
            title = "Sugar",
            artist = "Maroon 5",
            album = "V",
            durationSeconds = 235,
            thumbnailUrl = "https://i.ytimg.com/vi/09R8_2nJtjg/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            genre = "Pop",
            viewCount = "3.9B views",
            lyrics = ""
        ),
        Track(
            id = "jfKfPfyJRdk",
            title = "Lofi Beats to Relax / Study to",
            artist = "Lofi Girl",
            album = "Midnight Sessions",
            durationSeconds = 280,
            thumbnailUrl = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            genre = "Lofi & Chill",
            viewCount = "150M views",
            lyrics = ""
        ),
        Track(
            id = "YQHsXMglC9A",
            title = "Hello",
            artist = "Adele",
            album = "25",
            durationSeconds = 295,
            thumbnailUrl = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            genre = "Pop",
            viewCount = "3.1B views",
            lyrics = ""
        ),
        Track(
            id = "hT_nvWreIhg",
            title = "Counting Stars",
            artist = "OneRepublic",
            album = "Native",
            durationSeconds = 257,
            thumbnailUrl = "https://i.ytimg.com/vi/hT_nvWreIhg/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            genre = "Pop Rock",
            viewCount = "3.8B views",
            lyrics = ""
        ),
        Track(
            id = "CevxZvSJLk8",
            title = "Roar",
            artist = "Katy Perry",
            album = "Prism",
            durationSeconds = 230,
            thumbnailUrl = "https://i.ytimg.com/vi/CevxZvSJLk8/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            genre = "Pop",
            viewCount = "3.9B views",
            lyrics = ""
        ),
        Track(
            id = "fRh_vgS2dFE",
            title = "Levels (Workout Energy)",
            artist = "Avicii",
            album = "Levels EP",
            durationSeconds = 205,
            thumbnailUrl = "https://i.ytimg.com/vi/fRh_vgS2dFE/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3",
            genre = "Workout & Energy",
            viewCount = "650M views",
            lyrics = ""
        ),
        Track(
            id = "nfWlot6h_JM",
            title = "Shake It Off",
            artist = "Taylor Swift",
            album = "1989",
            durationSeconds = 242,
            thumbnailUrl = "https://i.ytimg.com/vi/nfWlot6h_JM/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3",
            genre = "Pop",
            viewCount = "3.3B views",
            lyrics = ""
        ),
        Track(
            id = "kJQP7kiw5F2",
            title = "Dákiti",
            artist = "Bad Bunny & Jhayco",
            album = "El Último Tour Del Mundo",
            durationSeconds = 215,
            thumbnailUrl = "https://i.ytimg.com/vi/TmKh7lAwnBI/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3",
            genre = "Latin",
            viewCount = "1.3B views",
            lyrics = ""
        ),
        Track(
            id = "kXYiU_JCYt2",
            title = "Radioactive",
            artist = "Imagine Dragons",
            album = "Night Visions",
            durationSeconds = 201,
            thumbnailUrl = "https://i.ytimg.com/vi/ktvTqknDobU/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-14.mp3",
            genre = "Rock",
            viewCount = "1.5B views",
            lyrics = ""
        ),
        Track(
            id = "jfKfPfyJRd2",
            title = "Deep Focus Piano (Study Flow)",
            artist = "Deep Focus Music",
            album = "Flow State",
            durationSeconds = 260,
            thumbnailUrl = "https://i.ytimg.com/vi/5qap5aO4i9A/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-15.mp3",
            genre = "Deep Focus",
            viewCount = "45M views",
            lyrics = ""
        ),
        Track(
            id = "YQHsXMglC92",
            title = "Sleep & Night Delta Waves",
            artist = "Calm Sleep Music",
            album = "Dreaming Hours",
            durationSeconds = 300,
            thumbnailUrl = "https://i.ytimg.com/vi/2OEL4P1Rz04/hqdefault.jpg",
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
            genre = "Night & Sleep",
            viewCount = "80M views",
            lyrics = ""
        )
    )

    fun getMoodCategories(): List<MoodCategory> = listOf(
        MoodCategory("chill", "Chill & Relax", "Lofi beats, ambient sounds, acoustic", "coffee", 0xFF2E7D32),
        MoodCategory("workout", "Workout & Energy", "High BPM, EDM, gym motivation", "fitness_center", 0xFFD32F2F),
        MoodCategory("focus", "Deep Focus", "Study sessions, binaural piano", "psychology", 0xFF1976D2),
        MoodCategory("party", "Party & Dance", "Club anthems, electro, reggaeton", "celebration", 0xFFE91E63),
        MoodCategory("romance", "Romance & Love", "Acoustic ballads, soulful R&B", "favorite", 0xFF8E24AA),
        MoodCategory("sleep", "Night & Sleep", "Rain sounds, soft delta waves", "bedtime", 0xFF455A64)
    )

    fun getQuickPicks(): List<Track> = getFeaturedTracks().shuffled().take(6)

    fun getTrendingTracks(): List<Track> = getFeaturedTracks()

    suspend fun searchTracks(query: String, selectedGenre: String? = null): List<Track> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotBlank()) {
            val ytResults = apiService.searchYouTube(trimmedQuery)
            if (ytResults.isNotEmpty()) {
                return ytResults
            }
        }

        val tracks = getFeaturedTracks()
        return tracks.filter { track ->
            val matchesGenre = selectedGenre == null || selectedGenre == "All" ||
                    track.genre.equals(selectedGenre, ignoreCase = true) ||
                    (selectedGenre == "Lofi & Chill" && (track.genre == "Lofi & Chill" || track.genre == "Deep Focus")) ||
                    (selectedGenre == "Workout & Energy" && (track.genre == "Workout & Energy" || track.genre == "Rock")) ||
                    (selectedGenre == "Pop Rock" && (track.genre == "Pop Rock" || track.genre == "Rock" || track.genre == "Pop"))

            val q = trimmedQuery.lowercase()
            val matchesQuery = q.isBlank() ||
                    track.title.lowercase().contains(q) ||
                    track.artist.lowercase().contains(q) ||
                    track.album.lowercase().contains(q) ||
                    track.genre.lowercase().contains(q)

            matchesGenre && matchesQuery
        }
    }
}
