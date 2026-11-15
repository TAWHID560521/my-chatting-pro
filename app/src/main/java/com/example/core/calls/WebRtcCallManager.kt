package com.example.core.calls

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.example.core.model.IceServerDto
import com.example.core.network.ApiService
import com.example.core.network.WebSocketManager
import com.example.core.network.WsEvent
import com.example.core.notifications.AppNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID

enum class CallStatus {
    IDLE,
    OUTGOING,
    INCOMING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED
}

data class ActiveCallUiState(
    val callId: String = "",
    val peerUserId: String = "",
    val peerDisplayName: String = "",
    val isVideo: Boolean = false,
    val isIncoming: Boolean = false,
    val status: CallStatus = CallStatus.IDLE,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val durationSeconds: Long = 0L,
    val errorMessage: String? = null
)

class WebRtcCallManager(
    private val context: Context,
    private val apiService: ApiService,
    private val webSocketManager: WebSocketManager,
    private val notificationManager: AppNotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val eglBase: EglBase = EglBase.create()

    private val _callState = MutableStateFlow(ActiveCallUiState())
    val callState: StateFlow<ActiveCallUiState> = _callState.asStateFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: VideoSink? = null
    private var remoteRenderer: VideoSink? = null
    private var remoteVideoTrack: VideoTrack? = null

    private var durationJob: Job? = null
    private var pendingRemoteOfferSdp: String? = null

    init {
        scope.launch {
            webSocketManager.events.collect { event ->
                if (event is WsEvent.CallSignal) {
                    handleSignalingEvent(event)
                }
            }
        }
    }

    fun attachRenderers(localSink: VideoSink?, remoteSink: VideoSink?) {
        localRenderer = localSink
        remoteRenderer = remoteSink
        localSink?.let { sink -> localVideoTrack?.addSink(sink) }
        remoteSink?.let { sink -> remoteVideoTrack?.addSink(sink) }
    }

    fun detachRenderers(localSink: VideoSink?, remoteSink: VideoSink?) {
        localSink?.let { localVideoTrack?.removeSink(it) }
        remoteSink?.let { remoteVideoTrack?.removeSink(it) }
        if (localRenderer == localSink) localRenderer = null
        if (remoteRenderer == remoteSink) remoteRenderer = null
    }

    fun startOutgoingCall(peerUserId: String, peerDisplayName: String, isVideo: Boolean) {
        if (_callState.value.status != CallStatus.IDLE && _callState.value.status != CallStatus.ENDED) return

        val callId = UUID.randomUUID().toString()
        _callState.value = ActiveCallUiState(
            callId = callId,
            peerUserId = peerUserId,
            peerDisplayName = peerDisplayName,
            isVideo = isVideo,
            isIncoming = false,
            status = CallStatus.OUTGOING,
            isSpeakerOn = isVideo,
            isCameraEnabled = isVideo
        )
        startForegroundCallService(peerDisplayName, isVideo)
        setSpeakerphone(isVideo)

        webSocketManager.sendCallSignal(
            JSONObject().apply {
                put("type", "call.invite")
                put("call_id", callId)
                put("to_user_id", peerUserId)
                put("call_kind", if (isVideo) "video" else "audio")
            }
        )
    }

    fun acceptIncomingCall() {
        val current = _callState.value
        if (current.status != CallStatus.INCOMING) return
        notificationManager.cancelNotification(current.callId)

        _callState.value = current.copy(status = CallStatus.CONNECTING)
        startForegroundCallService(current.peerDisplayName, current.isVideo)
        setSpeakerphone(current.isVideo)

        scope.launch {
            val prepared = preparePeerConnection(current.isVideo)
            if (!prepared) {
                endCall("Failed to initialize call media")
                return@launch
            }
            webSocketManager.sendCallSignal(
                JSONObject().apply {
                    put("type", "call.accept")
                    put("call_id", current.callId)
                    put("to_user_id", current.peerUserId)
                }
            )
            pendingRemoteOfferSdp?.let { sdp ->
                pendingRemoteOfferSdp = null
                applyRemoteOfferAndSendAnswer(current.callId, current.peerUserId, sdp)
            }
        }
    }

    fun rejectIncomingCall() {
        val current = _callState.value
        if (current.status != CallStatus.INCOMING) return
        notificationManager.cancelNotification(current.callId)
        webSocketManager.sendCallSignal(
            JSONObject().apply {
                put("type", "call.reject")
                put("call_id", current.callId)
                put("to_user_id", current.peerUserId)
            }
        )
        cleanupCall(CallStatus.IDLE)
    }

    fun endCall(errorMsg: String? = null) {
        val current = _callState.value
        if (current.callId.isNotBlank() && current.status != CallStatus.IDLE) {
            webSocketManager.sendCallSignal(
                JSONObject().apply {
                    put("type", "call.end")
                    put("call_id", current.callId)
                    put("to_user_id", current.peerUserId)
                }
            )
            notificationManager.cancelNotification(current.callId)
        }
        cleanupCall(if (errorMsg != null) CallStatus.ENDED else CallStatus.IDLE, errorMsg)
    }

    fun toggleMute() {
        val next = !_callState.value.isMuted
        localAudioTrack?.setEnabled(!next)
        _callState.value = _callState.value.copy(isMuted = next)
    }

    fun toggleSpeaker() {
        val next = !_callState.value.isSpeakerOn
        setSpeakerphone(next)
        _callState.value = _callState.value.copy(isSpeakerOn = next)
    }

    fun toggleCamera() {
        if (!_callState.value.isVideo) return
        val next = !_callState.value.isCameraEnabled
        localVideoTrack?.setEnabled(next)
        _callState.value = _callState.value.copy(isCameraEnabled = next)
    }

    fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                _callState.value = _callState.value.copy(isFrontCamera = isFrontCamera)
            }
            override fun onCameraSwitchError(errorDescription: String?) {}
        })
    }

    private fun handleSignalingEvent(signal: WsEvent.CallSignal) {
        when (signal.eventType) {
            "call.invite" -> {
                if (_callState.value.status != CallStatus.IDLE) {
                    webSocketManager.sendCallSignal(
                        JSONObject().apply {
                            put("type", "call.reject")
                            put("call_id", signal.callId)
                            put("to_user_id", signal.fromUserId)
                        }
                    )
                    return
                }
                val isVideo = signal.callKind == "video"
                val callerName = signal.fromDisplayName ?: "Incoming User"
                _callState.value = ActiveCallUiState(
                    callId = signal.callId,
                    peerUserId = signal.fromUserId,
                    peerDisplayName = callerName,
                    isVideo = isVideo,
                    isIncoming = true,
                    status = CallStatus.INCOMING,
                    isCameraEnabled = isVideo
                )
                notificationManager.showIncomingCallNotification(callerName, signal.callId)
            }

            "call.accept" -> {
                val current = _callState.value
                if (current.callId != signal.callId) return
                _callState.value = current.copy(status = CallStatus.CONNECTING)
                scope.launch {
                    val prepared = preparePeerConnection(current.isVideo)
                    if (!prepared) {
                        endCall("Failed to initialize WebRTC")
                        return@launch
                    }
                    createAndSendOffer(current.callId, current.peerUserId)
                }
            }

            "call.reject" -> {
                if (_callState.value.callId != signal.callId) return
                cleanupCall(CallStatus.ENDED, "Call declined")
            }

            "call.offer" -> {
                val sdp = signal.sdp ?: return
                val current = _callState.value
                if (current.callId != signal.callId) return
                if (peerConnection == null) {
                    pendingRemoteOfferSdp = sdp
                } else {
                    applyRemoteOfferAndSendAnswer(current.callId, current.peerUserId, sdp)
                }
            }

            "call.answer" -> {
                val sdp = signal.sdp ?: return
                if (_callState.value.callId != signal.callId) return
                peerConnection?.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp)
                )
            }

            "call.ice" -> {
                val cand = signal.iceCandidate ?: return
                val mid = signal.iceSdpMid ?: return
                val idx = signal.iceSdpMLineIndex ?: return
                if (_callState.value.callId != signal.callId) return
                peerConnection?.addIceCandidate(IceCandidate(mid, idx, cand))
            }

            "call.end" -> {
                val current = _callState.value
                if (current.callId == signal.callId) {
                    if (current.status == CallStatus.INCOMING) {
                        notificationManager.showMissedCallNotification(current.peerDisplayName, current.callId)
                    }
                    notificationManager.cancelNotification(current.callId)
                    cleanupCall(CallStatus.IDLE)
                }
            }
        }
    }

    private suspend fun preparePeerConnection(isVideo: Boolean): Boolean {
        return try {
            ensureFactoryInitialized()
            val factory = peerConnectionFactory ?: return false

            val iceServers = fetchIceServers()
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _callState.value = _callState.value.copy(status = CallStatus.CONNECTED)
                            startDurationTimer()
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _callState.value = _callState.value.copy(status = CallStatus.RECONNECTING)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            endCall("Call connection failed")
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {}
                        else -> {}
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    val current = _callState.value
                    webSocketManager.sendCallSignal(
                        JSONObject().apply {
                            put("type", "call.ice")
                            put("call_id", current.callId)
                            put("to_user_id", current.peerUserId)
                            put("candidate", candidate.sdp)
                            put("sdp_mid", candidate.sdpMid)
                            put("sdp_mline_index", candidate.sdpMLineIndex)
                        }
                    )
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {
                    stream?.videoTracks?.firstOrNull()?.let { track ->
                        remoteVideoTrack = track
                        remoteRenderer?.let { track.addSink(it) }
                    }
                }
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        remoteRenderer?.let { track.addSink(it) }
                    }
                }
            }) ?: return false

            peerConnection = pc

            val audioConstraints = MediaConstraints()
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", localAudioSource).apply {
                setEnabled(!_callState.value.isMuted)
            }
            pc.addTrack(localAudioTrack, listOf("ARDAMS"))

            if (isVideo) {
                val capturer = createCameraCapturer()
                if (capturer != null) {
                    videoCapturer = capturer
                    surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                    localVideoSource = factory.createVideoSource(capturer.isScreencast)
                    capturer.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
                    capturer.startCapture(720, 1280, 24)
                    localVideoTrack = factory.createVideoTrack("ARDAMSv0", localVideoSource).apply {
                        setEnabled(_callState.value.isCameraEnabled)
                        localRenderer?.let { addSink(it) }
                    }
                    pc.addTrack(localVideoTrack, listOf("ARDAMS"))
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun createAndSendOffer(callId: String, peerUserId: String) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (_callState.value.isVideo) "true" else "false"))
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                webSocketManager.sendCallSignal(
                    JSONObject().apply {
                        put("type", "call.offer")
                        put("call_id", callId)
                        put("to_user_id", peerUserId)
                        put("sdp", sdp.description)
                        put("sdp_type", sdp.type.canonicalForm())
                    }
                )
            }
        }, constraints)
    }

    private fun applyRemoteOfferAndSendAnswer(callId: String, peerUserId: String, sdp: String) {
        val pc = peerConnection ?: return
        pc.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    pc.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(answer: SessionDescription?) {
                            answer ?: return
                            pc.setLocalDescription(SimpleSdpObserver(), answer)
                            webSocketManager.sendCallSignal(
                                JSONObject().apply {
                                    put("type", "call.answer")
                                    put("call_id", callId)
                                    put("to_user_id", peerUserId)
                                    put("sdp", answer.description)
                                    put("sdp_type", answer.type.canonicalForm())
                                }
                            )
                        }
                    }, MediaConstraints())
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
    }

    private suspend fun fetchIceServers(): List<PeerConnection.IceServer> {
        val defaults = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        return try {
            val resp = apiService.getIceServers()
            val list: List<IceServerDto> = resp.body()?.iceServers.orEmpty()
            if (list.isEmpty()) defaults else list.map { dto ->
                val builder = PeerConnection.IceServer.builder(dto.urls)
                if (!dto.username.isNullOrBlank()) builder.setUsername(dto.username)
                if (!dto.credential.isNullOrBlank()) builder.setPassword(dto.credential)
                builder.createIceServer()
            }
        } catch (_: Exception) {
            defaults
        }
    }

    private fun ensureFactoryInitialized() {
        if (peerConnectionFactory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val back = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        val target = front ?: back ?: return null
        return enumerator.createCapturer(target, null)
    }

    private fun startDurationTimer() {
        if (durationJob != null) return
        durationJob = scope.launch {
            while (_callState.value.status == CallStatus.CONNECTED || _callState.value.status == CallStatus.RECONNECTING) {
                delay(1000L)
                _callState.value = _callState.value.copy(durationSeconds = _callState.value.durationSeconds + 1L)
            }
        }
    }

    private fun startForegroundCallService(peerName: String, isVideo: Boolean) {
        try {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra("peer_name", peerName)
                putExtra("is_video", isVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopForegroundCallService() {
        try {
            context.stopService(Intent(context, CallForegroundService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun setSpeakerphone(enabled: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (enabled) {
                    val speaker = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) am.setCommunicationDevice(speaker)
                } else {
                    am.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = enabled
            }
        } catch (_: Exception) {
        }
    }

    private fun cleanupCall(finalStatus: CallStatus, errorMsg: String? = null) {
        durationJob?.cancel()
        durationJob = null
        pendingRemoteOfferSdp = null
        stopForegroundCallService()

        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        try { videoCapturer?.dispose() } catch (_: Exception) {}
        videoCapturer = null

        localRenderer?.let { localVideoTrack?.removeSink(it) }
        remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
        remoteVideoTrack = null

        try { localVideoTrack?.dispose() } catch (_: Exception) {}
        localVideoTrack = null
        try { localVideoSource?.dispose() } catch (_: Exception) {}
        localVideoSource = null
        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        surfaceTextureHelper = null

        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        localAudioTrack = null
        try { localAudioSource?.dispose() } catch (_: Exception) {}
        localAudioSource = null

        try { peerConnection?.close() } catch (_: Exception) {}
        try { peerConnection?.dispose() } catch (_: Exception) {}
        peerConnection = null

        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        _callState.value = if (finalStatus == CallStatus.IDLE) {
            ActiveCallUiState()
        } else {
            _callState.value.copy(status = finalStatus, errorMessage = errorMsg)
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
