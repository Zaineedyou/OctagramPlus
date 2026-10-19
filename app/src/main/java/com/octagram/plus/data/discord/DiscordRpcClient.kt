package com.octagram.plus.data.discord

import android.util.Log
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Discord Gateway v10 client; the stored credential is an OAuth access token, never a user token. */
class DiscordRpcClient(
    private val onConnectionChanged: (Boolean) -> Unit = {},
) {
    private val lock = Any()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Octagram-Discord-RPC").apply { isDaemon = true }
    }

    private var socket: WebSocket? = null
    private var heartbeatTask: ScheduledFuture<*>? = null
    private var reconnectTask: ScheduledFuture<*>? = null
    private var accessToken: String? = null
    private var stopped = true
    private var identified = false
    private var sequence = 0
    private var lastHeartbeatAckAt = 0L
    private var reconnectDelaySeconds = 2L
    private var latestActivity: JSONObject? = null
    private var currentTrackKey: String? = null
    private var trackStartedAt = 0L
    private var wasPlaying = false
    private var currentStatus = "online"

    fun connect(oauthAccessToken: String) {
        val cleanToken = oauthAccessToken.trim()
        if (cleanToken.isEmpty()) return
        synchronized(lock) {
            if (accessToken == cleanToken && !stopped && socket != null) return
            disconnectLocked(clearPresence = false)
            accessToken = cleanToken
            stopped = false
            reconnectDelaySeconds = 2L
            onConnectionChanged(false)
            openSocketLocked()
        }
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
        synchronized(lock) {
            val cleanTitle = title.trim().take(128).ifEmpty { "Unknown track" }
            val cleanArtist = artist.trim().take(128)
            val key = "$cleanTitle\u0000$cleanArtist"
            if (key != currentTrackKey || (isPlaying && !wasPlaying) || trackStartedAt == 0L) {
                currentTrackKey = key
                trackStartedAt = (System.currentTimeMillis() - positionMs.coerceAtLeast(0L)).coerceAtLeast(1L)
            }
            wasPlaying = isPlaying

            if (!isPlaying && !settings.showWhilePaused) {
                latestActivity = null
                sendPresenceLocked(settings.accountStatus)
                return
            }

            val details = renderDiscordRpcTemplate(
                settings.detailsTemplate.ifBlank { "{song.name}" }, cleanTitle, cleanArtist, album, songId,
            ).take(128)
            val state = renderDiscordRpcTemplate(
                settings.stateTemplate.ifBlank { "{artist.name}" }, cleanTitle, cleanArtist, album, songId,
            ).take(128)
            val activity = JSONObject()
                .put("name", settings.activityName.trim().take(128))
                .put("type", settings.activityType.takeIf { it in DiscordRpcSettings.ActivityTypes } ?: 2)
            if (details.isNotBlank()) activity.put("details", details)
            if (state.isNotBlank()) activity.put("state", state)
            if (isPlaying && durationMs > 0L) {
                activity.put(
                    "timestamps",
                    JSONObject()
                        .put("start", trackStartedAt)
                        .put("end", trackStartedAt + durationMs),
                )
            }
            val buttonLabels = JSONArray()
            val buttonUrls = JSONArray()
            fun addButton(isEnabled: Boolean, label: String, urlTemplate: String) {
                if (!isEnabled || label.isBlank()) return
                val renderedUrl = renderDiscordRpcTemplate(
                    urlTemplate, cleanTitle, cleanArtist, album, songId,
                ).take(512)
                val uri = runCatching { Uri.parse(renderedUrl) }.getOrNull() ?: return
                if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) return
                buttonLabels.put(renderDiscordRpcTemplate(label, cleanTitle, cleanArtist, album, songId).take(32))
                buttonUrls.put(renderedUrl)
            }
            addButton(settings.button1Enabled, settings.button1Label, settings.button1Url)
            addButton(settings.button2Enabled, settings.button2Label, settings.button2Url)
            if (buttonLabels.length() > 0) {
                activity.put("buttons", buttonLabels)
                    .put("metadata", JSONObject().put("button_urls", buttonUrls))
            }
            latestActivity = activity
            sendPresenceLocked(settings.accountStatus)
        }
    }

    fun clearPresence() {
        synchronized(lock) {
            latestActivity = null
            currentTrackKey = null
            trackStartedAt = 0L
            wasPlaying = false
            sendPresenceLocked()
        }
    }

    fun clearAndDisconnect() {
        synchronized(lock) { disconnectLocked(clearPresence = true) }
    }

    private fun openSocketLocked() {
        if (stopped) return
        val request = Request.Builder().url(GATEWAY_URL).build()
        socket = httpClient.newWebSocket(request, gatewayListener)
    }

    private val gatewayListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "Discord Gateway transport opened")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
            synchronized(lock) {
                if (stopped || socket !== webSocket) return
                if (!payload.isNull("s")) sequence = payload.optInt("s", sequence)
                when (payload.optInt("op", -1)) {
                    10 -> handleHelloLocked(webSocket, payload.optJSONObject("d")?.optLong("heartbeat_interval", DEFAULT_HEARTBEAT_MS) ?: DEFAULT_HEARTBEAT_MS)
                    0 -> if (payload.optString("t") == "READY") handleReadyLocked(webSocket)
                    1 -> webSocket.send(heartbeatPayload())
                    7, 9 -> reconnectLocked(webSocket)
                    11 -> lastHeartbeatAckAt = System.currentTimeMillis()
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(lock) { reconnectLocked(webSocket) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Discord Gateway transport failed (${response?.code ?: t.javaClass.simpleName})")
            synchronized(lock) { reconnectLocked(webSocket) }
        }
    }

    private fun handleHelloLocked(webSocket: WebSocket, heartbeatIntervalMs: Long) {
        if (socket !== webSocket || stopped) return
        heartbeatTask?.cancel(false)
        val interval = heartbeatIntervalMs.coerceIn(1_000L, 120_000L)
        lastHeartbeatAckAt = System.currentTimeMillis()
        heartbeatTask = scheduler.scheduleAtFixedRate(
            {
                synchronized(lock) {
                    if (stopped || socket !== webSocket) return@synchronized
                    if (System.currentTimeMillis() - lastHeartbeatAckAt > interval * 2) {
                        reconnectLocked(webSocket)
                        return@synchronized
                    }
                    runCatching { webSocket.send(heartbeatPayload()) }
                }
            },
            (interval * 0.9).toLong().coerceAtLeast(1_000L),
            interval,
            TimeUnit.MILLISECONDS,
        )

        val token = accessToken ?: return
        val identify = JSONObject()
            .put("op", 2)
            .put(
                "d",
                JSONObject()
                    .put("token", "Bearer $token")
                    .put("intents", 0)
                    .put(
                        "properties",
                        JSONObject()
                            .put("os", "android")
                            .put("browser", "Discord Android")
                            .put("device", "Octagram+"),
                    )
                    .put("compress", false),
            )
        webSocket.send(identify.toString())
    }

    private fun handleReadyLocked(webSocket: WebSocket) {
        if (socket !== webSocket || stopped) return
        identified = true
        reconnectDelaySeconds = 2L
        onConnectionChanged(true)
        sendPresenceLocked()
        Log.i(TAG, "Discord Rich Presence ready")
    }

    private fun sendPresenceLocked(status: String = currentStatus) {
        currentStatus = status.takeIf { it in DiscordRpcSettings.AccountStatuses } ?: "online"
        val activeSocket = socket ?: return
        if (!identified || stopped) return
        val activities = JSONArray().apply { latestActivity?.let(::put) }
        val payload = JSONObject()
            .put("op", 3)
            .put(
                "d",
                JSONObject()
                    .put("since", 0)
                    .put("activities", activities)
                    .put("status", currentStatus)
                    .put("afk", false),
            )
        activeSocket.send(payload.toString())
    }

    private fun reconnectLocked(source: WebSocket) {
        if (stopped || socket !== source) return
        socket = null
        identified = false
        onConnectionChanged(false)
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        source.close(4000, "reconnect")
        reconnectTask?.cancel(false)
        val delaySeconds = min(reconnectDelaySeconds, MAX_RECONNECT_SECONDS)
        reconnectDelaySeconds = (delaySeconds * 2L).coerceAtMost(MAX_RECONNECT_SECONDS)
        reconnectTask = scheduler.schedule(
            { synchronized(lock) { if (!stopped && socket == null) openSocketLocked() } },
            delaySeconds,
            TimeUnit.SECONDS,
        )
    }

    private fun disconnectLocked(clearPresence: Boolean) {
        stopped = true
        if (clearPresence && identified) {
            latestActivity = null
            val activeSocket = socket
            if (activeSocket != null) {
                val clearPayload = JSONObject()
                    .put("op", 3)
                    .put(
                        "d",
                        JSONObject()
                            .put("since", JSONObject.NULL)
                            .put("activities", JSONArray())
                            .put("status", "online")
                            .put("afk", false),
                    )
                activeSocket.send(clearPayload.toString())
            }
        }
        identified = false
        onConnectionChanged(false)
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        reconnectTask?.cancel(false)
        reconnectTask = null
        socket?.close(1000, "RPC stopped")
        socket = null
        accessToken = null
        latestActivity = null
        currentTrackKey = null
        trackStartedAt = 0L
        wasPlaying = false
    }

    private fun heartbeatPayload(): String = JSONObject()
        .put("op", 1)
        .put("d", if (sequence > 0) sequence else JSONObject.NULL)
        .toString()

    companion object {
        private const val TAG = "DiscordRpcClient"
        private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
        private const val DEFAULT_HEARTBEAT_MS = 41_250L
        private const val MAX_RECONNECT_SECONDS = 32L
    }
}
