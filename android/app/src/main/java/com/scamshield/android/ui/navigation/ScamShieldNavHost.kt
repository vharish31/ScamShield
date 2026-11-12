package com.scamshield.android.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scamshield.android.data.auth.FirebaseAuthRepository
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.preferences.UserSettings
import com.scamshield.android.notifications.ScamNotificationManager
import com.scamshield.android.ui.analysis.AnalysisResultScreen
import com.scamshield.android.ui.analysis.AnalysisResultViewModel
import com.scamshield.android.ui.auth.AuthViewModel
import com.scamshield.android.ui.auth.ForgotPasswordScreen
import com.scamshield.android.ui.auth.LoginScreen
import com.scamshield.android.ui.auth.RegisterScreen
import com.scamshield.android.ui.contacts.TrustedContactsScreen
import com.scamshield.android.ui.contacts.TrustedContactsViewModel
import com.scamshield.android.ui.dashboard.DashboardScreen
import com.scamshield.android.ui.dashboard.DashboardViewModel
import com.scamshield.android.ui.history.HistoryScreen
import com.scamshield.android.ui.history.HistoryViewModel
import com.scamshield.android.ui.liveprotection.LiveProtectionScreen
import com.scamshield.android.ui.liveprotection.LiveProtectionViewModel
import com.scamshield.android.ui.notifications.NotificationsScreen
import com.scamshield.android.ui.notifications.NotificationsViewModel
import com.scamshield.android.ui.onboarding.OnboardingScreen
import com.scamshield.android.ui.onboarding.OnboardingViewModel
import com.scamshield.android.ui.settings.SettingsScreen
import com.scamshield.android.ui.settings.SettingsViewModel
import com.scamshield.android.ui.theme.RiskHighColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class ScamRoute(val route: String) {
    data object Splash : ScamRoute("splash")
    data object Onboarding : ScamRoute("onboarding")
    data object Login : ScamRoute("login")
    data object Register : ScamRoute("register")
    data object ForgotPassword : ScamRoute("forgot_password")
    data object Dashboard : ScamRoute("dashboard")
    data object LiveProtection : ScamRoute("live")
    data object History : ScamRoute("history")
    data object Contacts : ScamRoute("contacts")
    data object Settings : ScamRoute("settings")
    data object Notifications : ScamRoute("notifications")
    data object AnalysisResult : ScamRoute("analysis/{analysisId}") {
        fun buildRoute(analysisId: String): String = "analysis/${Uri.encode(analysisId)}"
    }
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomTabs = listOf(
    BottomNavTab(ScamRoute.Dashboard.route, "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    BottomNavTab(ScamRoute.LiveProtection.route, "Live", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    BottomNavTab(ScamRoute.History.route, "History", Icons.Filled.History, Icons.Outlined.History),
    BottomNavTab(ScamRoute.Contacts.route, "Contacts", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    BottomNavTab(ScamRoute.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

data class AppRootState(
    val isInitialized: Boolean = false,
    val isSignedIn: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val unreadNotificationsCount: Int = 0
)

@HiltViewModel
class AppRootViewModel @Inject constructor(
    authRepository: FirebaseAuthRepository,
    preferencesRepository: AppPreferencesRepository,
    notificationManager: ScamNotificationManager
) : ViewModel() {
    val rootState: StateFlow<AppRootState> = combine(
        authRepository.authStateFlow,
        preferencesRepository.settingsFlow,
        notificationManager.unreadCountFlow
    ) { user, settings, unread ->
        AppRootState(
            isInitialized = true,
            isSignedIn = user != null,
            settings = settings,
            unreadNotificationsCount = unread
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppRootState()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScamShieldRootNav(
    rootViewModel: AppRootViewModel,
    initialNotificationDestination: String?,
    initialAnalysisId: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val rootState by rootViewModel.rootState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showMainChrome = currentRoute in bottomTabs.map { it.route }

    LaunchedEffect(initialNotificationDestination, initialAnalysisId, rootState.isInitialized, rootState.isSignedIn) {
        if (!rootState.isInitialized || !rootState.isSignedIn) return@LaunchedEffect
        if (!initialAnalysisId.isNullOrBlank()) {
            navController.navigate(ScamRoute.AnalysisResult.buildRoute(initialAnalysisId)) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        } else if (initialNotificationDestination == "live") {
            navController.navigate(ScamRoute.LiveProtection.route) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }

    Scaffold(
        topBar = {
            if (showMainChrome) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Scam Shield",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AI Protection Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate(ScamRoute.Notifications.route) },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (rootState.unreadNotificationsCount > 0) {
                                        Badge(
                                            containerColor = RiskHighColor,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = rootState.unreadNotificationsCount.coerceAtMost(99).toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Alerts",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (showMainChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(ScamRoute.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScamRoute.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(ScamRoute.Splash.route) {
                LaunchedEffect(rootState.isInitialized, rootState.isSignedIn, rootState.settings.onboardingCompleted) {
                    if (!rootState.isInitialized) return@LaunchedEffect
                    val destination = when {
                        !rootState.settings.onboardingCompleted -> ScamRoute.Onboarding.route
                        !rootState.isSignedIn -> ScamRoute.Login.route
                        else -> ScamRoute.Dashboard.route
                    }
                    navController.navigate(destination) {
                        popUpTo(ScamRoute.Splash.route) { inclusive = true }
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            composable(ScamRoute.Onboarding.route) {
                val vm: OnboardingViewModel = hiltViewModel()
                OnboardingScreen(
                    viewModel = vm,
                    onContinue = {
                        val next = if (rootState.isSignedIn) ScamRoute.Dashboard.route else ScamRoute.Login.route
                        navController.navigate(next) {
                            popUpTo(ScamRoute.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(ScamRoute.Login.route) {
                val vm: AuthViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = vm,
                    onLoginSuccess = {
                        navController.navigate(ScamRoute.Dashboard.route) {
                            popUpTo(ScamRoute.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateRegister = { navController.navigate(ScamRoute.Register.route) },
                    onNavigateForgotPassword = { navController.navigate(ScamRoute.ForgotPassword.route) }
                )
            }

            composable(ScamRoute.Register.route) {
                val vm: AuthViewModel = hiltViewModel()
                RegisterScreen(
                    viewModel = vm,
                    onRegisterSuccess = {
                        navController.navigate(ScamRoute.Dashboard.route) {
                            popUpTo(ScamRoute.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(ScamRoute.ForgotPassword.route) {
                val vm: AuthViewModel = hiltViewModel()
                ForgotPasswordScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(ScamRoute.Dashboard.route) {
                val vm: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = vm,
                    onOpenLiveProtection = {
                        navController.navigate(ScamRoute.LiveProtection.route) {
                            popUpTo(ScamRoute.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenAnalysisDetail = { id ->
                        navController.navigate(ScamRoute.AnalysisResult.buildRoute(id))
                    },
                    onOpenContacts = {
                        navController.navigate(ScamRoute.Contacts.route) {
                            popUpTo(ScamRoute.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(ScamRoute.LiveProtection.route) {
                val vm: LiveProtectionViewModel = hiltViewModel()
                LiveProtectionScreen(
                    viewModel = vm,
                    onOpenAnalysisDetail = { id ->
                        navController.navigate(ScamRoute.AnalysisResult.buildRoute(id))
                    }
                )
            }

            composable(ScamRoute.History.route) {
                val vm: HistoryViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = vm,
                    onOpenAnalysisDetail = { id ->
                        navController.navigate(ScamRoute.AnalysisResult.buildRoute(id))
                    }
                )
            }

            composable(ScamRoute.Contacts.route) {
                val vm: TrustedContactsViewModel = hiltViewModel()
                TrustedContactsScreen(viewModel = vm)
            }

            composable(ScamRoute.Settings.route) {
                val vm: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = vm,
                    onSignedOut = {
                        navController.navigate(ScamRoute.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(ScamRoute.Notifications.route) {
                val vm: NotificationsViewModel = hiltViewModel()
                NotificationsScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenAnalysisDetail = { id ->
                        navController.navigate(ScamRoute.AnalysisResult.buildRoute(id))
                    }
                )
            }

            composable(
                route = ScamRoute.AnalysisResult.route,
                arguments = listOf(navArgument("analysisId") { type = NavType.StringType })
            ) {
                val vm: AnalysisResultViewModel = hiltViewModel()
                AnalysisResultScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
