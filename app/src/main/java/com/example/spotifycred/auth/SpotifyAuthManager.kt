package com.example.spotifycred.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.example.spotifycred.data.PlaylistItem
import com.example.spotifycred.data.SpotifyConstants
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

class SpotifyAuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("spotify_prefs", Context.MODE_PRIVATE)

    // PKCE values
    private lateinit var codeVerifier: String

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    /** STEP 1: Launch Spotify Auth with PKCE */
    fun startAuthFlow() {
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
            .launchUrl(context, authUri)
    }

    /** STEP 2: Exchange code for token */
    fun exchangeCodeForToken(code: String, onComplete: () -> Unit) {
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

                    onComplete()
                }
            }
        })
    }

    /** STEP 3: Retrieve profile data */
    fun fetchUserProfile(token: String, onComplete: (String, String) -> Unit) {
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

    fun fetchUserPlaylists(token: String, onComplete: (List<PlaylistItem>) -> Unit) {
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

                    onComplete(playlists)
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