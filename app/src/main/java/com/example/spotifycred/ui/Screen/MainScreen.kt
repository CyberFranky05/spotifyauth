package com.example.spotifycred.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.spotifycred.data.PlaylistItem
import com.example.spotifycred.ui.Screen.LoginScreen
import com.example.spotifycred.ui.Screen.ProfileScreen

@Composable
fun MainScreen(
    onAuthRequest: () -> Unit,
    onCheckExistingAuth: (String?, (String, String) -> Unit, (List<PlaylistItem>) -> Unit) -> Unit,
    getAccessToken: () -> String?
) {
    var displayName by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    // If we already have a stored token, load profile immediately
    LaunchedEffect(Unit) {
        val token = getAccessToken()
        onCheckExistingAuth(
            token,
            { name, mail -> displayName = name; email = mail },
            { playlistsList -> playlists = playlistsList }
        )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (displayName == null) {
            LoginScreen(onAuthRequest)
        } else {
            ProfileScreen(displayName!!, email!!, playlists)
        }
    }
}