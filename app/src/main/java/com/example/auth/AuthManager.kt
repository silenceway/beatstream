package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UserAccount(
    val isLoggedIn: Boolean = false,
    val name: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val accessToken: String = ""
)

class AuthManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("yt_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val CLIENT_ID = "1060693546940-bthgoeqi22qfa41uo0fdloqj2or2e37g.apps.googleusercontent.com"
        const val REDIRECT_URI = "https://gen-lang-client-0142480792.firebaseapp.com/__/auth/handler"
        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/youtube",
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email"
        ).joinToString(" ")
    }

    private val _accountState = MutableStateFlow(loadAccount())
    val accountState: StateFlow<UserAccount> = _accountState.asStateFlow()

    private fun loadAccount(): UserAccount {
        val token = prefs.getString("access_token", null)
        val name = prefs.getString("user_name", "") ?: ""
        val email = prefs.getString("user_email", "") ?: ""
        val avatar = prefs.getString("user_avatar", "") ?: ""

        return if (!token.isNullOrBlank()) {
            UserAccount(
                isLoggedIn = true,
                name = name,
                email = email,
                avatarUrl = avatar,
                accessToken = token
            )
        } else {
            UserAccount()
        }
    }

    fun getOAuthUrl(): String {
        return "$AUTH_URL?client_id=$CLIENT_ID" +
                "&redirect_uri=$REDIRECT_URI" +
                "&response_type=token" +
                "&scope=$SCOPES" +
                "&prompt=consent"
    }

    fun extractToken(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("ya29.")) {
            return trimmed
        }
        if (trimmed.contains("access_token=")) {
            val after = trimmed.substringAfter("access_token=")
            return after.substringBefore("&").substringBefore(" ")
        }
        return try {
            val uri = Uri.parse(trimmed)
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank() && fragment.contains("access_token=")) {
                fragment.substringAfter("access_token=").substringBefore("&")
            } else {
                uri.getQueryParameter("access_token")
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
        _accountState.update { it.copy(isLoggedIn = true, accessToken = token) }
    }

    fun updateProfile(name: String, email: String, avatarUrl: String) {
        prefs.edit()
            .putString("user_name", name)
            .putString("user_email", email)
            .putString("user_avatar", avatarUrl)
            .apply()

        _accountState.update {
            it.copy(
                isLoggedIn = true,
                name = name,
                email = email,
                avatarUrl = avatarUrl
            )
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _accountState.value = UserAccount()
    }
}
