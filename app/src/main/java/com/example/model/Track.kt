package com.example.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 210,
    val thumbnailUrl: String = "",
    val streamUrl: String = "",
    val lyrics: String = "",
    val genre: String = "Pop",
    val viewCount: String = "",
    val isFavorite: Boolean = false
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0
)

data class MoodCategory(
    val id: String,
    val name: String,
    val subtitle: String,
    val iconName: String,
    val colorHex: Long
)
