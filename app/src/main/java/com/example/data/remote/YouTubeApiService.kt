package com.example.data.remote

import android.util.Log
import com.example.model.Playlist
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UserProfile(
    val name: String,
    val email: String,
    val pictureUrl: String
)

class YouTubeApiService {

    private val tag = "YouTubeApiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchUserProfile(token: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val name = json.optString("name", "YouTube User")
                val email = json.optString("email", "")
                val picture = json.optString("picture", "")
                UserProfile(name, email, picture)
            } else {
                Log.e(tag, "fetchUserProfile error: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchUserProfile exception", e)
            null
        }
    }

    suspend fun fetchMyPlaylists(token: String): List<Playlist> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<Playlist>()
        try {
            val url = "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&mine=true&maxResults=50"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val idStr = item.optString("id", "")
                    val snippet = item.optJSONObject("snippet")
                    val contentDetails = item.optJSONObject("contentDetails")

                    val title = snippet?.optString("title", "Untitled Playlist") ?: "Untitled"
                    val desc = snippet?.optString("description", "") ?: ""
                    val count = contentDetails?.optInt("itemCount", 0) ?: 0

                    val thumbs = snippet?.optJSONObject("thumbnails")
                    val cover = thumbs?.optJSONObject("high")?.optString("url")
                        ?: thumbs?.optJSONObject("default")?.optString("url")
                        ?: ""

                    // Map string hash to Long id
                    val idLong = idStr.hashCode().toLong()

                    playlists.add(
                        Playlist(
                            id = idLong,
                            name = title,
                            description = desc,
                            coverUrl = cover,
                            trackCount = count
                        )
                    )
                }
            } else {
                Log.e(tag, "fetchMyPlaylists error: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchMyPlaylists exception", e)
        }
        playlists
    }

    suspend fun fetchLikedSongs(token: String): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        try {
            // 'LL' is YouTube's designated Liked Videos / Liked Music playlist
            val url = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&playlistId=LL&maxResults=50"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val snippet = item.optJSONObject("snippet")
                    val contentDetails = item.optJSONObject("contentDetails")

                    val videoId = contentDetails?.optString("videoId")
                        ?: snippet?.optJSONObject("resourceId")?.optString("videoId")
                        ?: ""

                    if (videoId.isNotBlank()) {
                        val title = snippet?.optString("title", "Track") ?: "Track"
                        val artist = snippet?.optString("videoOwnerChannelTitle", "YouTube Music") ?: "YouTube Music"
                        val thumbs = snippet?.optJSONObject("thumbnails")
                        val thumbUrl = thumbs?.optJSONObject("high")?.optString("url")
                            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                        tracks.add(
                            Track(
                                id = videoId,
                                title = title,
                                artist = artist,
                                durationSeconds = 210,
                                thumbnailUrl = thumbUrl,
                                streamUrl = "https://cdn.pixabay.com/download/audio/2022/10/14/audio_9939f792cb.mp3",
                                genre = "Liked on YouTube",
                                isFavorite = true
                            )
                        )
                    }
                }
            } else {
                Log.e(tag, "fetchLikedSongs error: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchLikedSongs exception", e)
        }
        tracks
    }

    suspend fun rateVideo(token: String, videoId: String, like: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val rating = if (like) "like" else "none"
            val url = "https://www.googleapis.com/youtube/v3/videos/rate?id=$videoId&rating=$rating"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post("".toRequestBody(null))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(tag, "rateVideo exception", e)
            false
        }
    }

    suspend fun createPlaylist(token: String, title: String, description: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/youtube/v3/playlists?part=snippet,status"
            val jsonBody = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("title", title)
                    put("description", description)
                })
                put("status", JSONObject().apply {
                    put("privacyStatus", "private")
                })
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(tag, "createPlaylist exception", e)
            false
        }
    }

    suspend fun addTrackToPlaylist(token: String, playlistId: String, videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet"
            val jsonBody = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("playlistId", playlistId)
                    put("resourceId", JSONObject().apply {
                        put("kind", "youtube#video")
                        put("videoId", videoId)
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(tag, "addTrackToPlaylist exception", e)
            false
        }
    }

    suspend fun searchYouTube(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<Track>()
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            var startMarker = "var ytInitialData = "
            var startIdx = html.indexOf(startMarker)
            if (startIdx == -1) {
                startMarker = "ytInitialData = "
                startIdx = html.indexOf(startMarker)
            }
            if (startIdx != -1) {
                val jsonStart = startIdx + startMarker.length
                val endIdx = html.indexOf(";</script>", jsonStart)
                if (endIdx != -1) {
                    val jsonStr = html.substring(jsonStart, endIdx)
                    val root = JSONObject(jsonStr)
                    findVideoRenderers(root, results)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "searchYouTube exception", e)
        }
        results
    }

    private fun findVideoRenderers(obj: Any?, results: MutableList<Track>) {
        if (results.size >= 25) return
        if (obj is JSONObject) {
            if (obj.has("videoRenderer")) {
                val vr = obj.getJSONObject("videoRenderer")
                val vid = vr.optString("videoId", "")
                val titleObj = vr.optJSONObject("title")
                val runs = titleObj?.optJSONArray("runs")
                val title = if (runs != null && runs.length() > 0) {
                    runs.getJSONObject(0).optString("text", "Unknown")
                } else {
                    titleObj?.optString("simpleText", "Unknown") ?: "Unknown"
                }

                val ownerObj = vr.optJSONObject("ownerText")
                val ownerRuns = ownerObj?.optJSONArray("runs")
                val artist = if (ownerRuns != null && ownerRuns.length() > 0) {
                    ownerRuns.getJSONObject(0).optString("text", "YouTube")
                } else {
                    "YouTube Artist"
                }

                val lengthObj = vr.optJSONObject("lengthText")
                val durationStr = lengthObj?.optString("simpleText", "3:30") ?: "3:30"
                val durationSec = parseDurationSeconds(durationStr)

                val viewsObj = vr.optJSONObject("shortViewCountText") ?: vr.optJSONObject("viewCountText")
                val views = viewsObj?.optString("simpleText", "") ?: ""

                if (vid.isNotBlank() && title.isNotBlank() && results.none { it.id == vid }) {
                    results.add(
                        Track(
                            id = vid,
                            title = title,
                            artist = artist,
                            album = "YouTube Music",
                            durationSeconds = durationSec,
                            thumbnailUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                            streamUrl = "",
                            lyrics = "",
                            genre = "YouTube",
                            viewCount = views
                        )
                    )
                }
            }
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                findVideoRenderers(obj.opt(key), results)
            }
        } else if (obj is JSONArray) {
            for (i in 0 until obj.length()) {
                findVideoRenderers(obj.opt(i), results)
            }
        }
    }

    private fun parseDurationSeconds(durationStr: String): Int {
        val parts = durationStr.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 210
        }
    }
}
