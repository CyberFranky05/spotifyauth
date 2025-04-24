package com.example.spotifycred.ui.Screen


import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@Composable
fun LoginScreen(onAuthRequest: () -> Unit) {
    Button(
        onClick = onAuthRequest,
        modifier = Modifier.padding(16.dp)
    ) {
        Text("Connect to Spotify", modifier = Modifier.padding(16.dp))
    }
}