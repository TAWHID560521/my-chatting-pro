package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.core.calls.CallStatus
import com.example.core.localization.LocalAppStrings
import com.example.features.addchat.AddChatScreen
import com.example.features.addchat.AddChatViewModel
import com.example.features.auth.AccountRecoveryScreen
import com.example.features.auth.AuthViewModel
import com.example.features.auth.CreateAccountScreen
import com.example.features.auth.LoginScreen
import com.example.features.auth.SplashScreen
import com.example.features.auth.WelcomeScreen
import com.example.features.calls.CallScreen
import com.example.features.chats.ChatsScreen
import com.example.features.chats.ChatsViewModel
import com.example.features.messages.ChatDetailScreen
import com.example.features.messages.ChatDetailViewModel
import com.example.features.messages.MediaViewerScreen
import com.example.features.profile.ProfileScreen
import com.example.features.profile.ProfileViewModel
import com.example.features.settings.ActiveDevicesScreen
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel
import com.example.ui.components.PermissionRationaleDialog
import kotlinx.serialization.Serializable

@Serializable object RouteSplash
@Serializable object RouteWelcome
@Serializable object RouteLogin
@Serializable object RouteCreateAccount
@Serializable object RouteAccountRecovery
@Serializable object RouteMainTabs
@Serializable data class RouteChatDetail(val conversationId: String)
@Serializable object RouteProfile
@Serializable object RouteActiveDevices
@Serializable data class RouteMediaViewer(
    val messageId: String,
    val mediaObjectId: String,
    val localPath: String?,
    val isVideo: Boolean,
    val title: String
)

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val callState by container.webRtcCallManager.callState.collectAsState()
    val session by container.preferencesManager.sessionFlow.collectAsState(initial = null)
    var wasLoggedIn by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        if (current.isLoggedIn) {
            wasLoggedIn = true
        } else if (wasLoggedIn) {
            wasLoggedIn = false
            navController.navigate(RouteWelcome) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = RouteSplash
    ) {
        composable<RouteSplash> {
            SplashScreen(
                preferencesManager = container.preferencesManager,
                onAuthenticated = {
                    navController.navigate(RouteMainTabs) {
                        popUpTo(RouteSplash) { inclusive = true }
                    }
                },
                onUnauthenticated = {
                    navController.navigate(RouteWelcome) {
                        popUpTo(RouteSplash) { inclusive = true }
                    }
                }
            )
        }

        composable<RouteWelcome> {
            val authVm: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(container.chatRepository, container.preferencesManager)
            )
            WelcomeScreen(
                viewModel = authVm,
                onNavigateToLogin = { navController.navigate(RouteLogin) },
                onNavigateToCreateAccount = { navController.navigate(RouteCreateAccount) }
            )
        }

        composable<RouteLogin> {
            val authVm: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(container.chatRepository, container.preferencesManager)
            )
            LoginScreen(
                viewModel = authVm,
                onBack = { navController.popBackStack() },
                onNavigateToCreateAccount = {
                    navController.navigate(RouteCreateAccount) {
                        popUpTo(RouteLogin) { inclusive = true }
                    }
                },
                onNavigateToRecovery = { navController.navigate(RouteAccountRecovery) },
                onLoginSuccess = {
                    navController.navigate(RouteMainTabs) {
                        popUpTo(RouteWelcome) { inclusive = true }
                    }
                }
            )
        }

        composable<RouteCreateAccount> {
            val authVm: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(container.chatRepository, container.preferencesManager)
            )
            CreateAccountScreen(
                viewModel = authVm,
                onBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(RouteLogin) {
                        popUpTo(RouteCreateAccount) { inclusive = true }
                    }
                },
                onAccountCreated = {
                    navController.navigate(RouteMainTabs) {
                        popUpTo(RouteWelcome) { inclusive = true }
                    }
                }
            )
        }

        composable<RouteAccountRecovery> {
            val authVm: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(container.chatRepository, container.preferencesManager)
            )
            AccountRecoveryScreen(
                viewModel = authVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable<RouteMainTabs> {
            MainTabsScreen(
                container = container,
                onOpenProfile = { navController.navigate(RouteProfile) },
                onOpenSecurityDevices = { navController.navigate(RouteActiveDevices) },
                onOpenChat = { convId -> navController.navigate(RouteChatDetail(convId)) },
                onSignedOutOrDeleted = {
                    navController.navigate(RouteWelcome) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<RouteChatDetail> { entry ->
            val route = entry.toRoute<RouteChatDetail>()
            val context = LocalContext.current
            val chatDetailVm: ChatDetailViewModel = viewModel(
                factory = ChatDetailViewModel.Factory(
                    appContext = context.applicationContext,
                    conversationId = route.conversationId,
                    repository = container.chatRepository,
                    preferencesManager = container.preferencesManager,
                    webSocketManager = container.webSocketManager,
                    voiceRecorderManager = container.voiceRecorderManager,
                    voicePlayerManager = container.voicePlayerManager
                )
            )
            ChatDetailScreen(
                viewModel = chatDetailVm,
                onBack = { navController.popBackStack() },
                onStartCall = { peerId, peerName, isVideo ->
                    container.webRtcCallManager.startOutgoingCall(peerId, peerName, isVideo)
                },
                onOpenMediaViewer = { msgId, mediaId, localPath, isVideo, title ->
                    navController.navigate(
                        RouteMediaViewer(
                            messageId = msgId,
                            mediaObjectId = mediaId,
                            localPath = localPath,
                            isVideo = isVideo,
                            title = title
                        )
                    )
                }
            )
        }

        composable<RouteProfile> {
            val profileVm: ProfileViewModel = viewModel(
                factory = ProfileViewModel.Factory(container.chatRepository, container.preferencesManager)
            )
            ProfileScreen(
                viewModel = profileVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable<RouteActiveDevices> {
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(container.chatRepository, container.preferencesManager)
            )
            ActiveDevicesScreen(
                viewModel = settingsVm,
                onBack = { navController.popBackStack() },
                onCurrentDeviceSignedOut = {
                    navController.navigate(RouteWelcome) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<RouteMediaViewer> { entry ->
            val route = entry.toRoute<RouteMediaViewer>()
            MediaViewerScreen(
                repository = container.chatRepository,
                mediaObjectId = route.mediaObjectId,
                localPath = route.localPath,
                isVideo = route.isVideo,
                title = route.title,
                onBack = { navController.popBackStack() }
            )
        }
    }

    // Full-screen WebRTC Call Overlay whenever an incoming/outgoing/active call exists
    if (callState.status != CallStatus.IDLE) {
        CallScreen(callManager = container.webRtcCallManager)
    }
}

@Composable
private fun MainTabsScreen(
    container: AppContainer,
    onOpenProfile: () -> Unit,
    onOpenSecurityDevices: () -> Unit,
    onOpenChat: (String) -> Unit,
    onSignedOutOrDeleted: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showNotifRationale by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Request POST_NOTIFICATIONS only after authenticated entry on Android 13+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                showNotifRationale = true
            }
        }
    }

    val chatsVm: ChatsViewModel = viewModel(
        factory = ChatsViewModel.Factory(
            appContext = context.applicationContext,
            repository = container.chatRepository,
            preferencesManager = container.preferencesManager,
            webSocketManager = container.webSocketManager
        )
    )
    val addChatVm: AddChatViewModel = viewModel(
        factory = AddChatViewModel.Factory(container.chatRepository, container.preferencesManager)
    )
    val settingsVm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(container.chatRepository, container.preferencesManager)
    )

    // Strictly ONLY 3 tabs per Requirement 8: Chats, Add Chat, Settings
    val tabs = listOf(
        Triple(strings.navChats, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
        Triple(strings.navAddChat, Icons.Filled.PersonAdd, Icons.Outlined.PersonAdd),
        Triple(strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    tabs.forEachIndexed { idx, (label, filled, outlined) ->
                        NavigationRailItem(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == idx) filled else outlined,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("nav_rail_tab_$idx")
                        )
                    }
                }
                TabContent(
                    selectedTab = selectedTab,
                    chatsVm = chatsVm,
                    addChatVm = addChatVm,
                    settingsVm = settingsVm,
                    onOpenProfile = onOpenProfile,
                    onOpenSecurityDevices = onOpenSecurityDevices,
                    onOpenChat = onOpenChat,
                    onGoToAddChat = { selectedTab = 1 },
                    onSignedOutOrDeleted = onSignedOutOrDeleted,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { idx, (label, filled, outlined) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("bottom_nav_tab_$idx")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                TabContent(
                    selectedTab = selectedTab,
                    chatsVm = chatsVm,
                    addChatVm = addChatVm,
                    settingsVm = settingsVm,
                    onOpenProfile = onOpenProfile,
                    onOpenSecurityDevices = onOpenSecurityDevices,
                    onOpenChat = onOpenChat,
                    onGoToAddChat = { selectedTab = 1 },
                    onSignedOutOrDeleted = onSignedOutOrDeleted,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    if (showNotifRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionRationaleDialog(
            reasonText = strings.notificationPermissionReason,
            onConfirm = {
                showNotifRationale = false
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { showNotifRationale = false }
        )
    }
}

@Composable
private fun TabContent(
    selectedTab: Int,
    chatsVm: ChatsViewModel,
    addChatVm: AddChatViewModel,
    settingsVm: SettingsViewModel,
    onOpenProfile: () -> Unit,
    onOpenSecurityDevices: () -> Unit,
    onOpenChat: (String) -> Unit,
    onGoToAddChat: () -> Unit,
    onSignedOutOrDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        when (selectedTab) {
            0 -> ChatsScreen(
                viewModel = chatsVm,
                onOpenProfile = onOpenProfile,
                onOpenChat = { onOpenChat(it.id) },
                onGoToAddChat = onGoToAddChat
            )
            1 -> AddChatScreen(
                viewModel = addChatVm,
                onOpenConversation = { onOpenChat(it.id) }
            )
            2 -> SettingsScreen(
                viewModel = settingsVm,
                onOpenProfile = onOpenProfile,
                onOpenSecurityDevices = onOpenSecurityDevices,
                onAccountSignedOutOrDeleted = onSignedOutOrDeleted
            )
        }
    }
}
