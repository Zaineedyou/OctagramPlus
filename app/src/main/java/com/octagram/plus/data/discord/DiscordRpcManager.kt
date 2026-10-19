package com.octagram.plus.data.discord

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

object DiscordRpcManager {
    enum class ConnectionStatus { Disconnected, Authorizing, Connecting, Connected }

    data class OAuthTokens(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var tokenStore: DiscordRpcTokenStore? = null
    @Volatile private var accessToken: String? = null
    @Volatile private var initialized = false
    private val rpcClient = DiscordRpcClient { connected ->
        _connectionStatus.value = when {
            connected -> ConnectionStatus.Connected
            _isAuthenticated.value -> ConnectionStatus.Connecting
            else -> ConnectionStatus.Disconnected
        }
        if (connected) scope.launch { refreshUsername() }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        appContext = app
        tokenStore = DiscordRpcTokenStore(app)
        tokenStore?.clearLegacyToken()
        accessToken = tokenStore?.readAccessToken()
        _isAuthenticated.value = !accessToken.isNullOrBlank()
        initialized = true

        val enabled = app.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .getBoolean("discord_rpc_enabled", false)
        if (!accessToken.isNullOrBlank()) {
            scope.launch {
                refreshUsername()
                if (enabled) connectWithSavedToken()
            }
        }
    }

    suspend fun authorize(activity: Activity): Boolean {
        initialize(activity)
        val verifier = randomUrlSafe(48)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = randomUrlSafe(24)
        val callback = DiscordOAuthActivity.beginAuthorization()
        _connectionStatus.value = ConnectionStatus.Authorizing
        _lastError.value = null

        val authorizeUrl = Uri.parse(OAUTH_AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", OAUTH_SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()

        return try {
            withContext(Dispatchers.Main) {
                try {
                    CustomTabsIntent.Builder().build().launchUrl(activity, authorizeUrl)
                } catch (_: ActivityNotFoundException) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, authorizeUrl))
                }
            }
            val code = withTimeout(OAUTH_TIMEOUT_MS) { callback.await() }
            if (code.state != state) throw IllegalStateException("Discord OAuth state did not match")
            val tokens = exchangeCode(code.code, verifier)
            saveTokens(tokens, null)
            _connectionStatus.value = ConnectionStatus.Connecting
            _isAuthenticated.value = true
            accessToken = tokens.accessToken
            refreshUsername()
            rpcClient.connect(tokens.accessToken)
            true
        } catch (e: CancellationException) {
            DiscordOAuthActivity.cancelAuthorization()
            _connectionStatus.value = if (_isAuthenticated.value) ConnectionStatus.Disconnected else ConnectionStatus.Disconnected
            _lastError.value = "Login dibatalkan atau waktunya habis. Coba hubungkan Discord lagi."
            false
        } catch (e: Exception) {
            DiscordOAuthActivity.cancelAuthorization()
            _connectionStatus.value = ConnectionStatus.Disconnected
            _lastError.value = when {
                e is ActivityNotFoundException -> "Browser tidak tersedia untuk membuka Discord."
                e.message?.contains("state", ignoreCase = true) == true -> "Verifikasi OAuth gagal. Coba hubungkan Discord lagi."
                else -> "Gagal menghubungkan Discord. Periksa koneksi internet dan coba lagi."
            }
            false
        }
    }

    fun cancelAuthorization() {
        DiscordOAuthActivity.cancelAuthorization()
        _connectionStatus.value = if (_isAuthenticated.value) ConnectionStatus.Disconnected else ConnectionStatus.Disconnected
    }

    fun updateTrack(
        title: String,
        artist: String,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean,
        album: String,
        songId: String,
        settings: DiscordRpcSettings,
    ) {
        if (!initialized) appContext?.let(::initialize) ?: return
        scope.launch {
            val token = validAccessToken() ?: run {
                rpcClient.clearAndDisconnect()
                return@launch
            }
            _connectionStatus.value = if (_connectionStatus.value == ConnectionStatus.Connected) ConnectionStatus.Connected else ConnectionStatus.Connecting
            rpcClient.connect(token)
            rpcClient.updateTrack(title, artist, durationMs, positionMs, isPlaying, album, songId, settings)
        }
    }

    fun clearPresence() = rpcClient.clearPresence()

    fun clearAndDisconnect() {
        rpcClient.clearAndDisconnect()
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    fun logout() {
        DiscordOAuthActivity.cancelAuthorization()
        tokenStore?.clear()
        accessToken = null
        _isAuthenticated.value = false
        _currentUsername.value = null
        _lastError.value = null
        rpcClient.clearAndDisconnect()
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    fun clearLastError() {
        _lastError.value = null
    }

    private suspend fun connectWithSavedToken() {
        val token = validAccessToken() ?: return
        _connectionStatus.value = ConnectionStatus.Connecting
        rpcClient.connect(token)
    }

    private suspend fun validAccessToken(): String? {
        val store = tokenStore ?: return null
        var token = accessToken ?: store.readAccessToken() ?: return null
        val now = System.currentTimeMillis() / 1000L
        val expiresAt = store.expiresAtEpochSeconds()
        if (expiresAt > now && expiresAt - now > TOKEN_REFRESH_WINDOW_SECONDS) return token

        val refresh = store.readRefreshToken()
        if (refresh.isNullOrBlank()) {
            if (expiresAt > now) return token
            _lastError.value = "Sesi Discord kedaluwarsa. Hubungkan akun lagi."
            logout()
            return null
        }

        return try {
            val updated = requestToken(
                mapOf("grant_type" to "refresh_token", "refresh_token" to refresh),
            )
            saveTokens(updated, refresh)
            token = updated.accessToken
            accessToken = token
            token
        } catch (_: Exception) {
            if (expiresAt <= now) {
                _lastError.value = "Sesi Discord kedaluwarsa. Hubungkan akun lagi."
                logout()
                null
            } else {
                token
            }
        }
    }

    private suspend fun exchangeCode(code: String, verifier: String): OAuthTokens = withContext(Dispatchers.IO) {
        requestToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier,
            ),
        )
    }

    private suspend fun requestToken(extraFields: Map<String, String>): OAuthTokens = withContext(Dispatchers.IO) {
        val form = linkedMapOf("client_id" to CLIENT_ID).apply { putAll(extraFields) }
        val body = form.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val connection = (URL(OAUTH_TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val responseBody = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Discord OAuth returned HTTP $code")
            val json = JSONObject(responseBody)
            OAuthTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
                expiresInSeconds = json.optLong("expires_in", 0L),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun saveTokens(tokens: OAuthTokens, oldRefreshToken: String?) {
        tokenStore?.saveTokens(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: oldRefreshToken,
            expiresInSeconds = tokens.expiresInSeconds,
        )
    }

    private suspend fun refreshUsername() {
        val token = accessToken ?: tokenStore?.readAccessToken() ?: return
        val username = withContext(Dispatchers.IO) {
            val connection = (URL(USER_ME_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext null
                val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                json.optString("global_name").takeIf(String::isNotBlank)
                    ?: json.optString("username").takeIf(String::isNotBlank)
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }
        _currentUsername.value = username
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private const val CLIENT_ID = "1447278780795064401"
    private const val REDIRECT_URI = "metrolistdiscord://oauth2/callback"
    private const val OAUTH_SCOPES = "openid sdk.social_layer_presence"
    private const val OAUTH_AUTHORIZE_URL = "https://discord.com/oauth2/authorize"
    private const val OAUTH_TOKEN_URL = "https://discord.com/api/v10/oauth2/token"
    private const val USER_ME_URL = "https://discord.com/api/v10/users/@me"
    private const val OAUTH_TIMEOUT_MS = 120_000L
    private const val TOKEN_REFRESH_WINDOW_SECONDS = 3_600L
}
