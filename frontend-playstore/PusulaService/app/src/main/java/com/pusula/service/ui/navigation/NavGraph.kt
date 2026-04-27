package com.pusula.service.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.pusula.service.BuildConfig
import com.pusula.service.ui.auth.AuthViewModel
import com.pusula.service.ui.auth.LoginScreen
import com.pusula.service.ui.auth.RegisterScreen
import com.pusula.service.ui.admin.CatalogScreen
import com.pusula.service.ui.admin.FieldRadarScreen
import com.pusula.service.ui.admin.PlanUpgradeScreen
import com.pusula.service.ui.admin.ProfitAnalysisScreen
import com.pusula.service.ui.main.MainScreen
import com.pusula.service.ui.technician.BarcodeScannerScreen
import com.pusula.service.ui.technician.CollectionScreen
import com.pusula.service.ui.technician.SignatureScreen
import com.pusula.service.ui.technician.TicketDetailScreen
import com.pusula.service.ui.technician.TicketViewModel
import com.pusula.service.util.PdfGenerator
import kotlinx.serialization.Serializable

private const val NAV_ANIM_DURATION_MS = 220
private val TICKET_DEEP_LINK_BASE = BuildConfig.TICKET_DEEP_LINK_BASE

private fun AnimatedContentTransitionScope<*>.defaultEnter(): EnterTransition =
    fadeIn(animationSpec = tween(NAV_ANIM_DURATION_MS))

private fun AnimatedContentTransitionScope<*>.defaultExit(): ExitTransition =
    fadeOut(animationSpec = tween(NAV_ANIM_DURATION_MS))

private fun AnimatedContentTransitionScope<*>.defaultPopEnter(): EnterTransition =
    fadeIn(animationSpec = tween(NAV_ANIM_DURATION_MS))

private fun AnimatedContentTransitionScope<*>.defaultPopExit(): ExitTransition =
    fadeOut(animationSpec = tween(NAV_ANIM_DURATION_MS))

sealed class Screen {
    @Serializable
    data object Login : Screen()

    @Serializable
    data object Register : Screen()

    @Serializable
    data object Main : Screen()

    @Serializable
    data class TicketDetail(val id: Long) : Screen()

    @Serializable
    data class BarcodeScanner(val ticketId: Long) : Screen()

    @Serializable
    data class Collection(val ticketId: Long) : Screen()

    @Serializable
    data class Signature(val ticketId: Long) : Screen()

    @Serializable
    data object FieldRadar : Screen()

    @Serializable
    data object ProfitAnalysis : Screen()

    @Serializable
    data object Catalog : Screen()

    @Serializable
    data object PlanUpgrade : Screen()
}

@Composable
fun PusulaNavGraph(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val session by authViewModel.sessionManager.state.collectAsState()
    val uiState by authViewModel.uiState.collectAsState()
    val startDestination: Screen = if (session.isAuthenticated) Screen.Main else Screen.Login

    LaunchedEffect(session.isAuthenticated) {
        if (session.isAuthenticated) {
            navController.navigate(Screen.Main) {
                popUpTo(Screen.Login) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            navController.navigate(Screen.Login) {
                popUpTo(0)
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { defaultEnter() },
        exitTransition = { defaultExit() },
        popEnterTransition = { defaultPopEnter() },
        popExitTransition = { defaultPopExit() }
    ) {
        composable<Screen.Login> {
            val context = LocalContext.current
            LoginScreen(
                uiState = uiState,
                onLogin = authViewModel::login,
                onGoogleLogin = { authViewModel.loginWithGoogle(context) },
                onNavigateRegister = { navController.navigate(Screen.Register) }
            )
        }
        composable<Screen.Register> {
            val context = LocalContext.current
            RegisterScreen(
                uiState = uiState,
                onRegister = authViewModel::register,
                onGoogleRegister = { preferredUsername ->
                    authViewModel.registerWithGoogle(context, preferredUsername)
                }
            )
        }
        composable<Screen.Main> {
            MainScreen(
                sessionManager = authViewModel.sessionManager,
                onLogout = { authViewModel.sessionManager.logout() },
                onDeleteAccount = { authViewModel.deleteAccount() },
                onUpgrade = { navController.navigate(Screen.PlanUpgrade) },
                onOpenTicket = { navController.navigate(Screen.TicketDetail(it)) },
                onNavigateFieldRadar = { navController.navigate(Screen.FieldRadar) },
                onNavigateProfitAnalysis = { navController.navigate(Screen.ProfitAnalysis) },
                onNavigateCatalog = { navController.navigate(Screen.Catalog) }
            )
        }
        composable<Screen.ProfitAnalysis> { ProfitAnalysisScreen() }
        composable<Screen.Catalog> { CatalogScreen() }
        composable<Screen.FieldRadar> { FieldRadarScreen() }
        composable<Screen.PlanUpgrade> { PlanUpgradeScreen() }
        composable<Screen.TicketDetail>(
            deepLinks = listOf(
                navDeepLink<Screen.TicketDetail>(basePath = TICKET_DEEP_LINK_BASE)
            )
        ) { backStack ->
            val ticketId = backStack.toRoute<Screen.TicketDetail>().id
            val vm: TicketViewModel = hiltViewModel()
            TicketDetailScreen(
                ticketId = ticketId,
                onOpenBarcode = { navController.navigate(Screen.BarcodeScanner(it)) },
                onOpenCollection = { navController.navigate(Screen.Collection(it)) },
                onOpenSignature = { navController.navigate(Screen.Signature(it)) },
                onGeneratePdf = {
                    val ticket = vm.uiState.value.selectedTicket
                    if (ticket != null) {
                        PdfGenerator.generateAndShare(
                            context = navController.context,
                            ticket = ticket,
                            parts = vm.uiState.value.usedParts,
                            technicianName = authViewModel.sessionManager.state.value.fullName
                        )
                    }
                }
            )
        }
        composable<Screen.BarcodeScanner> { backStack ->
            val ticketId = backStack.toRoute<Screen.BarcodeScanner>().ticketId
            BarcodeScannerScreen(ticketId = ticketId, onDone = { navController.popBackStack() })
        }
        composable<Screen.Collection> { backStack ->
            val ticketId = backStack.toRoute<Screen.Collection>().ticketId
            CollectionScreen(ticketId = ticketId, onDone = { navController.popBackStack() })
        }
        composable<Screen.Signature> { backStack ->
            val ticketId = backStack.toRoute<Screen.Signature>().ticketId
            SignatureScreen(ticketId = ticketId, onDone = { navController.popBackStack() })
        }
    }
}
