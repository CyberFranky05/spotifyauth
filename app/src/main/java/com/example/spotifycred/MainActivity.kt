package com.example.spotifycred

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.spotifycred.auth.SpotifyAuthManager
import com.example.spotifycred.ui.MainScreen
import com.example.spotifycred.ui.theme.SpotifycredTheme

class MainActivity : ComponentActivity() {
    private val authManager by lazy { SpotifyAuthManager(this) }
    var authCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SpotifycredTheme {
                MainScreen(
                    onAuthRequest = { authManager.startAuthFlow() },
                    onCheckExistingAuth = { token, profileCallback, playlistsCallback ->
                        if (token != null) {
                            authManager.fetchUserProfile(token, profileCallback)
                            authManager.fetchUserPlaylists(token, playlistsCallback)
                        }
                    },
                    getAccessToken = { authManager.getAccessToken() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.getQueryParameter("code")?.let { code ->
            authManager.exchangeCodeForToken(code) {
                authCallback?.invoke()
                recreate()
            }
        }
    }
}