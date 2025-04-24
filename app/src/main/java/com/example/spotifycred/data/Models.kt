package com.example.spotifycred.data

// Constants for Spotify Auth
object SpotifyConstants {
    const val CLIENT_ID = "" // Replace with your actual client ID
    const val REDIRECT_URI = "com.example.spotifycred://callback"
    const val AUTH_TOKEN_REQUEST_CODE = 0x10
    const val REQUEST_CODE = 1337
}

// Data model for playlists
data class PlaylistItem(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String?
)