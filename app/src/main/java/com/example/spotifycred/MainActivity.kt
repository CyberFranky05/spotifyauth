package com.example.spotifycred

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifycred.ui.theme.SpotifycredTheme
import com.example.spotifycred.ui.theme.greenColor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.collections.forEach
import kotlin.compareTo

class MainActivity : ComponentActivity() {
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("spotify_prefs", MODE_PRIVATE)
    }

    // PKCE values
    private lateinit var codeVerifier: String

    var authCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SpotifycredTheme {
                var displayName by remember { mutableStateOf<String?>(null) }
                var email by remember { mutableStateOf<String?>(null) }
                var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

                // If we already have a stored token, load profile immediately
                LaunchedEffect(Unit) {
                    prefs.getString("access_token", null)?.let { token ->
                        fetchUserProfile(token) { name, mail ->
                            displayName = name
                            email = mail
                        }
                        // Also fetch user playlists
                        fetchUserPlaylists(token) { playlistsList ->
                            playlists = playlistsList
                        }
                    }
                }

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (displayName == null) {
                        Button(onClick = { startAuthFlow() }) {
                            Text("Connect to Spotify", modifier = Modifier.padding(16.dp))
                        }
                    } else {
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
                }
            }
        }
    }

    /** STEP 1: Launch Spotify Auth with PKCE */
    private fun startAuthFlow() {
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val authUri = Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .appendPath("authorize")
            .appendQueryParameter("client_id", SpotifyConstants.CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", SpotifyConstants.REDIRECT_URI)
            .appendQueryParameter("scope", "user-read-private user-read-email playlist-read-private playlist-read-collaborative")
            .appendQueryParameter("state", UUID.randomUUID().toString())
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()

        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, authUri)
    }

    /** STEP 2: Handle redirect and exchange code for tokens */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.getQueryParameter("code")?.let { code ->
            exchangeCodeForToken(code)
        }
    }

    private fun exchangeCodeForToken(code: String) {
        val client = OkHttpClient()
        val form = FormBody.Builder()
            .add("client_id", SpotifyConstants.CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", SpotifyConstants.REDIRECT_URI)
            .add("code_verifier", codeVerifier)
            .build()
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(form)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* handle error */ }
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { json ->
                    val obj = JSONObject(json)
                    val accessToken = obj.getString("access_token")
                    val refreshToken = obj.getString("refresh_token")

                    // Store tokens
                    prefs.edit()
                        .putString("access_token", accessToken)
                        .putString("refresh_token", refreshToken)
                        .apply()

                    // STEP 3: Fetch user profile
                    fetchUserProfile(accessToken) { name, mail ->
                        // Also fetch playlists
                        fetchUserPlaylists(accessToken) { _ ->
                            runOnUiThread {
                                authCallback?.invoke()
                                recreate()
                            }
                        }
                    }
                }
            }
        })
    }

    /** STEP 3: Retrieve /me profile */
    private fun fetchUserProfile(token: String, onComplete: (String, String) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* handle error */ }
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { json ->
                    val obj = JSONObject(json)
                    val name = obj.optString("display_name")
                    val email = obj.optString("email")
                    onComplete(name, email)
                }
            }
        })
    }

    private fun fetchUserPlaylists(token: String, onComplete: (List<PlaylistItem>) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/playlists?limit=20")
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* handle error */ }
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { json ->
                    val obj = JSONObject(json)
                    val items = obj.getJSONArray("items")
                    val playlists = mutableListOf<PlaylistItem>()

                    for (i in 0 until items.length()) {
                        val playlist = items.getJSONObject(i)
                        val id = playlist.getString("id")
                        val name = playlist.getString("name")
                        val trackCount = playlist.getJSONObject("tracks").getInt("total")
                        val imageUrl = if (playlist.getJSONArray("images").length() > 0) {
                            playlist.getJSONArray("images").getJSONObject(0).getString("url")
                        } else null

                        playlists.add(PlaylistItem(id, name, trackCount, imageUrl))
                    }

                    runOnUiThread {
                        onComplete(playlists)
                    }
                }
            }
        })
    }

    /** UTIL: Generate a high-entropy PKCE code verifier */
    private fun generateCodeVerifier(): String =
        ByteArray(64).also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }

    /** UTIL: Create SHA256 code challenge */
    private fun generateCodeChallenge(verifier: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun homeScreen(modifier: Modifier = Modifier) {
    var isAuthenticated by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Spotify",
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontSize = 48.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = greenColor
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!isAuthenticated) {
                    SpotifyLoginButton(
                        modifier = Modifier.padding(bottom = 16.dp),
                        onLoginSuccess = {
                            isAuthenticated = true
                        }
                    )
                } else {
                    Text("Successfully authenticated with Spotify!",
                        style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
fun SpotifyLoginButton(modifier: Modifier = Modifier, onLoginSuccess: () -> Unit) {
    val ctx = LocalContext.current
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = greenColor,
        ),
        onClick = {
            openSpotifyAuth(ctx, onLoginSuccess)
        }
    ) {
        Text("Connect to Spotify",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(16.dp)
        )
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist image
            if (playlist.imageUrl != null) {
                AsyncImage(
                    model = playlist.imageUrl,
                    contentDescription = "Playlist cover",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(greenColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBox,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }
}

fun openSpotifyAuth(context: Context, onLoginSuccess: () -> Unit) {
    val state = UUID.randomUUID().toString()
    val builder = getSpotifyAuthRequest(state)

    val spotifyAuthUrl = "https://accounts.spotify.com/authorize?" +
            "client_id=${SpotifyConstants.CLIENT_ID}" +
            "&response_type=code" +
            "&redirect_uri=${Uri.encode(SpotifyConstants.REDIRECT_URI)}" +
            "&scope=user-read-private%20user-read-email%20playlist-read-private%20playlist-read-collaborative" +
            "&state=$state"

    val intent = builder.build()
    intent.intent.setPackage("com.android.chrome")
    intent.launchUrl(context, Uri.parse(spotifyAuthUrl))

    // Handle the result in the MainActivity's onNewIntent method
    if (context is MainActivity) {
        context.authCallback = onLoginSuccess
    }
}

fun getSpotifyAuthRequest(state: String): CustomTabsIntent.Builder {
    val builder = CustomTabsIntent.Builder()
    builder.setShowTitle(true)
    builder.setInstantAppsEnabled(true)
    builder.setToolbarColor(greenColor.hashCode())
    return builder
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DefaultPreview() {
    SpotifycredTheme {
        homeScreen()
    }
}