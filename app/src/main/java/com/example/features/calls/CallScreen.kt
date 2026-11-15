package com.example.features.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.core.calls.CallStatus
import com.example.core.calls.WebRtcCallManager
import com.example.core.localization.DateTimeFormatterUtil
import com.example.core.localization.LocalAppStrings
import com.example.ui.components.AvatarView
import com.example.ui.theme.ObsidianHeroBg
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    callManager: WebRtcCallManager
) {
    val strings = LocalAppStrings.current
    val state by callManager.callState.collectAsState()
    val context = LocalContext.current

    val acceptPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            callManager.acceptIncomingCall()
        } else {
            callManager.rejectIncomingCall()
        }
    }

    val statusText = when (state.status) {
        CallStatus.OUTGOING -> strings.outgoingCallState
        CallStatus.INCOMING -> strings.incomingCallTitle
        CallStatus.CONNECTING -> strings.ringingCallState
        CallStatus.CONNECTED -> DateTimeFormatterUtil.formatDurationSeconds(state.durationSeconds)
        CallStatus.RECONNECTING -> strings.reconnectingCallState
        CallStatus.ENDED -> state.errorMessage ?: strings.endedCallState
        CallStatus.IDLE -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianHeroBg, Color(0xFF061519))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (state.isVideo) {
            val remoteRenderer = remember { SurfaceViewRenderer(context) }
            val localRenderer = remember { SurfaceViewRenderer(context) }

            DisposableEffect(Unit) {
                remoteRenderer.init(callManager.eglBase.eglBaseContext, null)
                remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                localRenderer.init(callManager.eglBase.eglBaseContext, null)
                localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                localRenderer.setZOrderMediaOverlay(true)
                localRenderer.setMirror(true)
                callManager.attachRenderers(localRenderer, remoteRenderer)
                onDispose {
                    callManager.detachRenderers(localRenderer, remoteRenderer)
                    remoteRenderer.release()
                    localRenderer.release()
                }
            }

            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )

            if (state.isCameraEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 110.dp, height = 155.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { localRenderer },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!state.isVideo || state.status != CallStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(36.dp))
                    AvatarView(
                        displayName = state.peerDisplayName.ifBlank { "U" },
                        avatarUrl = null,
                        size = 96.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
                Text(
                    text = state.peerDisplayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFCCFBF1)
                )
            }

            if (state.status == CallStatus.INCOMING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = { callManager.rejectIncomingCall() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("reject_call_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = strings.rejectCall, modifier = Modifier.size(30.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(strings.rejectCall, color = Color.White)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = {
                                val required = if (state.isVideo) {
                                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                                } else {
                                    arrayOf(Manifest.permission.RECORD_AUDIO)
                                }
                                val missing = required.filter {
                                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                }
                                if (missing.isEmpty()) {
                                    callManager.acceptIncomingCall()
                                } else {
                                    acceptPermsLauncher.launch(missing.toTypedArray())
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("accept_call_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = strings.acceptCall, modifier = Modifier.size(30.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(strings.acceptCall, color = Color.White)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { callManager.toggleMute() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.isMuted) Color.White else Color.White.copy(alpha = 0.18f),
                                contentColor = if (state.isMuted) Color.Black else Color.White
                            ),
                            modifier = Modifier.size(52.dp).testTag("call_mute_button")
                        ) {
                            Icon(
                                imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (state.isMuted) strings.unmuteMic else strings.muteMic
                            )
                        }

                        FilledIconButton(
                            onClick = { callManager.toggleSpeaker() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.isSpeakerOn) Color.White else Color.White.copy(alpha = 0.18f),
                                contentColor = if (state.isSpeakerOn) Color.Black else Color.White
                            ),
                            modifier = Modifier.size(52.dp).testTag("call_speaker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = if (state.isSpeakerOn) strings.speakerOn else strings.speakerOff
                            )
                        }

                        if (state.isVideo) {
                            FilledIconButton(
                                onClick = { callManager.toggleCamera() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (!state.isCameraEnabled) Color.White else Color.White.copy(alpha = 0.18f),
                                    contentColor = if (!state.isCameraEnabled) Color.Black else Color.White
                                ),
                                modifier = Modifier.size(52.dp).testTag("call_camera_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (state.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = if (state.isCameraEnabled) strings.cameraOn else strings.cameraOff
                                )
                            }

                            FilledIconButton(
                                onClick = { callManager.switchCamera() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.18f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(52.dp).testTag("call_flip_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = strings.flipCamera
                                )
                            }
                        }

                        FilledIconButton(
                            onClick = { callManager.endCall() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(58.dp).testTag("end_call_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = strings.endCall)
                        }
                    }
                }
            }
        }
    }
}
