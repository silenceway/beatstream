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

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        dao.insertPlaylist(
            PlaylistEntity(
                name = name,
                description = description,
                coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80"
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
                    streamUrl = track.streamUrl,
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

    // Curated catalog with verified audio streams
    fun getFeaturedTracks(): List<Track> = listOf(
        Track(
            id = "fJ9rUzIMcZQ",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationSeconds = 359,
            thumbnailUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=epic-hollywood-trailer-9489.mp3",
            genre = "Rock",
            viewCount = "1.7B views",
            lyrics = "Is this the real life? Is this just fantasy?\nCaught in a landslide, no escape from reality.\nOpen your eyes, look up to the skies and see...\nI'm just a poor boy, I need no sympathy\nBecause I'm easy come, easy go, little high, little low\nAnyway the wind blows doesn't really matter to me, to me."
        ),
        Track(
            id = "4NRXx6U8ABQ",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/10/14/audio_9939f792cb.mp3?filename=synthwave-80s-110045.mp3",
            genre = "Pop",
            viewCount = "920M views",
            lyrics = "Yeah\nI've been tryna call\nI've been on my own for long enough\nMaybe you can show me how to love, maybe\nI'm going through withdrawals\nYou don't even have to do too much\nYou can turn me on with just a touch, baby\n\nI look around and Sin City's cold and empty\nNo one's around to judge me\nI can't see clearly when you're gone\n\nI said, ooh, I'm blinded by the lights\nNo, I can't sleep until I feel your touch."
        ),
        Track(
            id = "JGwWNGJdvx8",
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "÷ (Divide)",
            durationSeconds = 233,
            thumbnailUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/01/18/audio_d0a13f69d2.mp3?filename=tropical-summer-10903.mp3",
            genre = "Pop",
            viewCount = "6.2B views",
            lyrics = "The club isn't the best place to find a lover\nSo the bar is where I go\nMe and my friends at the table doing shots\nDrinking fast and then we talk slow\n\nCome over and start up a conversation with just me\nAnd trust me I'll give it a chance now\nTook my hand, stop, put Van the Man on the jukebox\nAnd then we start to dance\n\nGirl, you know I want your love\nYour love was handmade for somebody like me."
        ),
        Track(
            id = "kXYiU_JCYtU",
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            durationSeconds = 187,
            thumbnailUrl = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/03/15/audio_c8c8a73502.mp3?filename=energetic-indie-rock-10360.mp3",
            genre = "Rock",
            viewCount = "2.1B views",
            lyrics = "I'm tired of being what you want me to be\nFeeling so faithless, lost under the surface\nDon't know what you're expecting of me\nPut under the pressure of walking in your shoes\n\nCaught in the undertow, just caught in the undertow\n\nI've become so numb, I can't feel you there\nBecome so tired, so much more aware\nBy becoming this all I want to do\nIs be more like me and be less like you."
        ),
        Track(
            id = "kJQP7kiw5Fk",
            title = "Despacito",
            artist = "Luis Fonsi ft. Daddy Yankee",
            album = "Vida",
            durationSeconds = 228,
            thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/03/24/audio_33383a8f4c.mp3?filename=latin-summer-party-10499.mp3",
            genre = "Latin",
            viewCount = "8.4B views",
            lyrics = "¡Ay!\nFonsi, D.Y.\nOh, oh no, oh no, oh\nHey, yeah\nDididiri Daddy, go!\n\nSí, sabes que ya llevo un rato mirándote\nTengo que bailar contigo hoy\nVi que tu mirada ya estaba llamándome\nMuéstrame el camino que yo voy\n\nTú, tú eres el imán y yo soy el metal\nMe voy acercando y voy armando el plan\nSolo con pensarlo se acelera el pulso."
        ),
        Track(
            id = "09R8_2nJtjg",
            title = "Sugar",
            artist = "Maroon 5",
            album = "V",
            durationSeconds = 235,
            thumbnailUrl = "https://i.ytimg.com/vi/09R8_2nJtjg/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3?filename=funky-groove-873.mp3",
            genre = "Pop",
            viewCount = "3.9B views",
            lyrics = "I'm hurting, baby, I'm broken down\nI need your loving, loving, I need it now\nWhen I'm without you, I'm something weak\nYou got me begging, begging, I'm on my knees\n\nI don't wanna be needing your love\nI just wanna be deep in your love\nAnd it's killing me when you're away\nOoh, baby, 'cause a bullet don't care where it's hurt."
        ),
        Track(
            id = "jfKfPfyJRdk",
            title = "Lofi Beats to Relax / Study to",
            artist = "Lofi Girl",
            album = "Midnight Sessions",
            durationSeconds = 280,
            thumbnailUrl = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/05/16/audio_db6591201e.mp3?filename=lofi-study-112191.mp3",
            genre = "Lofi & Chill",
            viewCount = "150M views",
            lyrics = "[Instrumental - Calm vinyl crackle with jazz piano keys and soft boom-bap rhythm]\n\nRelax your mind, take a deep breath,\nand let the melody carry away your thoughts."
        ),
        Track(
            id = "YQHsXMglC9A",
            title = "Hello",
            artist = "Adele",
            album = "25",
            durationSeconds = 295,
            thumbnailUrl = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/02/07/audio_d0b6b3e83b.mp3?filename=piano-moment-9835.mp3",
            genre = "Pop",
            viewCount = "3.1B views",
            lyrics = "Hello, it's me\nI was wondering if after all these years you'd like to meet\nTo go over everything\nThey say that time's supposed to heal ya, but I ain't done much healing\n\nHello, can you hear me?\nI'm in California dreaming about who we used to be\nWhen we were younger and free\nI've forgotten how it felt before the world fell at our feet."
        ),
        Track(
            id = "hT_nvWreIhg",
            title = "Counting Stars",
            artist = "OneRepublic",
            album = "Native",
            durationSeconds = 257,
            thumbnailUrl = "https://i.ytimg.com/vi/hT_nvWreIhg/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/04/27/audio_30b3558cb7.mp3?filename=happy-folk-acoustic-guitar-111161.mp3",
            genre = "Pop Rock",
            viewCount = "3.8B views",
            lyrics = "Lately, I've been, I've been losing sleep\nDreaming about the things that we could be\nBut baby, I've been, I've been praying hard\nSaid, no more counting dollars, we'll be counting stars\n\nYeah, we'll be counting stars\n\nI see this life like a swinging vine\nSwing my heart across the line\nIn my face is flashing signs\nSeek it out and you shall find."
        ),
        Track(
            id = "CevxZvSJLk8",
            title = "Roar",
            artist = "Katy Perry",
            album = "Prism",
            durationSeconds = 230,
            thumbnailUrl = "https://i.ytimg.com/vi/CevxZvSJLk8/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/01/26/audio_d0c6514753.mp3?filename=powerful-stylish-rock-10029.mp3",
            genre = "Pop",
            viewCount = "3.9B views",
            lyrics = "I used to bite my tongue and hold my breath\nScared to rock the boat and make a mess\nSo I sat quietly, agreed politely\nI guess that I forgot I had a choice\n\nI let you push me past the breaking point\nI stood for nothing, so I fell for everything\n\nYou held me down, but I got up\nAlready brushing off the dust\nYou hear my voice, you hear that sound\nLike thunder, gonna shake the ground."
        ),
        Track(
            id = "fRh_vgS2dFE",
            title = "Sorry",
            artist = "Justin Bieber",
            album = "Purpose",
            durationSeconds = 205,
            thumbnailUrl = "https://i.ytimg.com/vi/fRh_vgS2dFE/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/01/18/audio_6122d10330.mp3?filename=modern-dance-10902.mp3",
            genre = "Pop",
            viewCount = "3.6B views",
            lyrics = "You gotta go and get angry at all of my honesty\nYou know I try, but I don't do too well with apologies\nI hope I don't run out of time, could someone call a referee?\n'Cause I just need one more shot at forgiveness\n\nI know you know that I made those mistakes maybe once or twice\nAnd by once or twice I mean maybe a couple a hundred times\nSo let me, oh, let me redeem, oh, redeem myself tonight\n'Cause I just need one more shot at second chances."
        ),
        Track(
            id = "nfWlot6h_JM",
            title = "Shake It Off",
            artist = "Taylor Swift",
            album = "1989",
            durationSeconds = 242,
            thumbnailUrl = "https://i.ytimg.com/vi/nfWlot6h_JM/hqdefault.jpg",
            streamUrl = "https://cdn.pixabay.com/download/audio/2022/02/10/audio_fc8621422a.mp3?filename=cheerful-pop-dance-10072.mp3",
            genre = "Pop",
            viewCount = "3.3B views",
            lyrics = "I stay out too late\nGot nothing in my brain\nThat's what people say, mm-mm\nThat's what people say, mm-mm\n\nI go on too many dates\nBut I can't make 'em stay\nAt least that's what people say, mm-mm\nThat's what people say, mm-mm\n\nBut I keep cruising\nCan't stop, won't stop moving\nIt's like I got this music in my mind\nSaying, 'It's gonna be alright'."
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

    fun searchTracks(query: String, selectedGenre: String? = null): List<Track> {
        val tracks = getFeaturedTracks()
        return tracks.filter { track ->
            val matchesGenre = selectedGenre == null || selectedGenre == "All" || track.genre.equals(selectedGenre, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.album.contains(query, ignoreCase = true) ||
                track.lyrics.contains(query, ignoreCase = true)
            matchesGenre && matchesQuery
        }
    }
}
