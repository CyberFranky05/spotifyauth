package com.example.spotifycred.ui.Screen


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spotifycred.data.PlaylistItem

@Composable
fun ProfileScreen(displayName: String, email: String, playlists: List<PlaylistItem>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Hello, $displayName", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Email: $email")
        Spacer(Modifier.height(24.dp))

        Text(
            "Your Playlists",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        if (playlists.isEmpty()) {
            CircularProgressIndicator()
        } else {
            playlists.forEach { playlist ->
                PlaylistCard(playlist)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}