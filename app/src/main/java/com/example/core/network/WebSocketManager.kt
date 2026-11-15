package com.example.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.core.config.AppConfig
import com.example.core.local.PreferencesManager
import com.example.core.model.MessageDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

sealed interface WsEvent {
    data class NewMessage(val message: MessageDto, val cursor: String?) : WsEvent
    data class MessageReceipt(
        val messageId: String,
        val conversationId: String,
        val status: String,
        val deliveredAt: String?,
        val readAt: String?,
        val cursor: String?
    ) : WsEvent
    data class MessageDeleted(
        val messageId: String,
        val conversationId: String,
        val deletedAt: String,
        val cursor: String?
    ) : WsEvent
    data class PresenceUpdate(
        val userId: String,
        val isOnline: Boolean,
        val lastSeenAt: String?
    ) : WsEvent
    data class TypingUpdate(
        val conversationId: String,
        val userId: String,
        val isTyping: Boolean
    ) : WsEvent
    data class CallSignal(
        val eventType: String,
        val callId: String,
        val fromUserId: String,
        val fromDisplayName: String?,
        val callKind: String?,
        val sdp: String?,
        val sdpType: String?,
        val iceCandidate: String?,
        val iceSdpMid: String?,
        val iceSdpMLineIndex: Int?
    ) : WsEvent
    data object SyncRequired : WsEvent
}

class WebSocketManager(
    context: Context,
    private val preferencesManager: PreferencesManager,
    private val moshi: Moshi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val wsClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(checkCurrentNetwork(context))
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldStayConnected = false
    private var reconnectAttempt = 0
    private val seenEventIds = LinkedHashSet<String>()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isNetworkAvailable.value = true
            if (shouldStayConnected && _connectionState.value != WsConnectionState.CONNECTED) {
                reconnectAttempt = 0
                connectNow()
            }
        }

        override fun onLost(network: Network) {
            _isNetworkAvailable.value = checkCurrentNetwork(context)
            if (!_isNetworkAvailable.value) {
                _connectionState.value = WsConnectionState.DISCONNECTED
            }
        }
    }

    init {
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(req, networkCallback)
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    fun start() {
        shouldStayConnected = true
        connectNow()
    }

    fun stop() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "User session closed")
        activeWebSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    fun sendTyping(conversationId: String, isTyping: Boolean) {
        sendJson(
            JSONObject().apply {
                put("type", "typing")
                put("conversation_id", conversationId)
                put("is_typing", isTyping)
            }
        )
    }

    fun sendCallSignal(payload: JSONObject) {
        sendJson(payload)
    }

    private fun sendJson(json: JSONObject): Boolean {
        return activeWebSocket?.send(json.toString()) ?: false
    }

    private fun connectNow() {
        reconnectJob?.cancel()
        scope.launch {
            val session = preferencesManager.currentSession()
            if (!session.isLoggedIn || !shouldStayConnected) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                return@launch
            }
            if (!_isNetworkAvailable.value) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                return@launch
            }

            activeWebSocket?.cancel()
            _connectionState.value = if (reconnectAttempt == 0) {
                WsConnectionState.CONNECTING
            } else {
                WsConnectionState.RECONNECTING
            }

            val apiBase = preferencesManager.currentApiBaseUrl()
            val wsUrl = AppConfig.deriveWsUrl(apiBase)
            val cursor = preferencesManager.currentWsCursor()

            val reqBuilder = Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer ${session.accessToken}")
            if (!cursor.isNullOrBlank()) {
                reqBuilder.header("X-Last-Event-Cursor", cursor)
            }

            activeWebSocket = wsClient.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0
                    _connectionState.value = WsConnectionState.CONNECTED
                    _events.tryEmit(WsEvent.SyncRequired)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingFrame(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    activeWebSocket = null
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    activeWebSocket = null
                    scheduleReconnect()
                }
            })
        }
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) {
            _connectionState.value = WsConnectionState.DISCONNECTED
            return
        }
        _connectionState.value = WsConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffSeconds = min(30L, 1L shl min(reconnectAttempt, 5))
            reconnectAttempt++
            delay(backoffSeconds * 1000L)
            if (shouldStayConnected) {
                connectNow()
            }
        }
    }

    private fun handleIncomingFrame(raw: String) {
        try {
            val root = JSONObject(raw)
            val eventId = root.optString("event_id")
            if (eventId.isNotBlank()) {
                synchronized(seenEventIds) {
                    if (seenEventIds.contains(eventId)) return
                    seenEventIds.add(eventId)
                    if (seenEventIds.size > 500) {
                        val first = seenEventIds.first()
                        seenEventIds.remove(first)
                    }
                }
            }

            val cursor = root.optString("cursor").takeIf { it.isNotBlank() }
            if (cursor != null) {
                scope.launch { preferencesManager.saveWsCursor(cursor) }
            }

            when (val type = root.optString("type")) {
                "message.new" -> {
                    val payloadStr = root.optJSONObject("payload")?.toString() ?: return
                    val msg = moshi.adapter(MessageDto::class.java).fromJson(payloadStr) ?: return
                    _events.tryEmit(WsEvent.NewMessage(msg, cursor))
                }
                "message.receipt" -> {
                    val p = root.optJSONObject("payload") ?: return
                    _events.tryEmit(
                        WsEvent.MessageReceipt(
                            messageId = p.getString("message_id"),
                            conversationId = p.getString("conversation_id"),
                            status = p.getString("delivery_status"),
                            deliveredAt = p.optString("delivered_at").takeIf { it.isNotBlank() },
                            readAt = p.optString("read_at").takeIf { it.isNotBlank() },
                            cursor = cursor
                        )
                    )
                }
                "message.deleted" -> {
                    val p = root.optJSONObject("payload") ?: return
                    _events.tryEmit(
                        WsEvent.MessageDeleted(
                            messageId = p.getString("message_id"),
                            conversationId = p.getString("conversation_id"),
                            deletedAt = p.getString("deleted_at"),
                            cursor = cursor
                        )
                    )
                }
                "presence.update" -> {
                    val p = root.optJSONObject("payload") ?: return
                    _events.tryEmit(
                        WsEvent.PresenceUpdate(
                            userId = p.getString("user_id"),
                            isOnline = p.optBoolean("is_online", false),
                            lastSeenAt = p.optString("last_seen_at").takeIf { it.isNotBlank() }
                        )
                    )
                }
                "typing" -> {
                    val p = root.optJSONObject("payload") ?: return
                    _events.tryEmit(
                        WsEvent.TypingUpdate(
                            conversationId = p.getString("conversation_id"),
                            userId = p.getString("user_id"),
                            isTyping = p.optBoolean("is_typing", false)
                        )
                    )
                }
                "call.invite", "call.accept", "call.reject", "call.offer", "call.answer", "call.ice", "call.end" -> {
                    val p = root.optJSONObject("payload") ?: return
                    _events.tryEmit(
                        WsEvent.CallSignal(
                            eventType = type,
                            callId = p.optString("call_id"),
                            fromUserId = p.optString("from_user_id"),
                            fromDisplayName = p.optString("from_display_name").takeIf { it.isNotBlank() },
                            callKind = p.optString("call_kind").takeIf { it.isNotBlank() },
                            sdp = p.optString("sdp").takeIf { it.isNotBlank() },
                            sdpType = p.optString("sdp_type").takeIf { it.isNotBlank() },
                            iceCandidate = p.optString("candidate").takeIf { it.isNotBlank() },
                            iceSdpMid = p.optString("sdp_mid").takeIf { it.isNotBlank() },
                            iceSdpMLineIndex = if (p.has("sdp_mline_index")) p.optInt("sdp_mline_index") else null
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore malformed frame safely without logging sensitive payload
        }
    }

    private fun checkCurrentNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }
}
