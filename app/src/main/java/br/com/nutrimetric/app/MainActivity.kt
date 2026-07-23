package br.com.nutrimetric.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.nutrimetric.app.repository.AuthRepository
import br.com.nutrimetric.app.ui.screens.AnalysisScreen
import br.com.nutrimetric.app.ui.screens.BarcodeScannerScreen
import br.com.nutrimetric.app.ui.screens.CameraScreen
import br.com.nutrimetric.app.ui.screens.ChatScreen
import br.com.nutrimetric.app.ui.screens.EditMealScreen
import br.com.nutrimetric.app.ui.screens.HomeScreen
import br.com.nutrimetric.app.ui.screens.LoginScreen
import br.com.nutrimetric.app.ui.screens.ManualEntryScreen
import br.com.nutrimetric.app.ui.screens.OnboardingScreen
import br.com.nutrimetric.app.ui.screens.PaywallScreen
import br.com.nutrimetric.app.ui.screens.SettingsScreen
import br.com.nutrimetric.app.ui.screens.HistoryScreen
import br.com.nutrimetric.app.ui.screens.WeeklyReportScreen
import br.com.nutrimetric.app.ui.theme.MyApplicationTheme
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()
          val context = androidx.compose.ui.platform.LocalContext.current
          val application = context.applicationContext as android.app.Application
          val viewModel: MainViewModel = viewModel(
              factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
          )

          // Login só é exigido quando o Firebase está configurado
          // (google-services.json presente). Antes disso o app roda em modo dev.
          val firebaseConfigured = remember { AuthRepository.isFirebaseConfigured(context) }
          val authRepository = remember { AuthRepository() }
          val startDestination =
              if (firebaseConfigured && !authRepository.isLoggedIn()) "login" else "home"

          NavHost(navController = navController, startDestination = startDestination) {
            composable("login") {
              LoginScreen(
                authRepository = authRepository,
                viewModel = viewModel,
                onLoginSuccess = {
                  navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                  }
                }
              )
            }
            composable("home") {
              HomeScreen(
                viewModel = viewModel,
                onNavigateToAnalysis = { navController.navigate("analysis") },
                onNavigateToCamera = { navController.navigate("camera") },
                onNavigateToManualEntry = { navController.navigate("manual_entry") },
                onNavigateToBarcode = { navController.navigate("barcode") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToEditMeal = { mealId -> navController.navigate("edit_meal/$mealId") },
                onNavigateToOnboarding = {
                  navController.navigate("onboarding") {
                    popUpTo("home") { inclusive = true }
                  }
                },
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToWeeklyReport = { navController.navigate("weekly_report") }
              )
            }
            composable("weekly_report") {
              WeeklyReportScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate("paywall?reason=weekly_report_locked") }
              )
            }
            composable("onboarding") {
              OnboardingScreen(
                viewModel = viewModel,
                onFinished = {
                  navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                  }
                }
              )
            }
            composable("barcode") {
              BarcodeScannerScreen(
                onBarcodeDetected = { barcode -> 
                    viewModel.scanBarcode(barcode)
                    navController.navigate("analysis") {
                        popUpTo("home")
                    }
                },
                onBack = { navController.popBackStack() }
              )
            }
            composable("history") {
              HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToEditMeal = { mealId -> navController.navigate("edit_meal/$mealId") },
                onNavigateToPaywall = { navController.navigate("paywall") }
              )
            }
            composable("settings") {
              SettingsScreen(
                viewModel = viewModel,
                authRepository = authRepository,
                onBack = { navController.popBackStack() },
                onSignedOut = {
                  navController.navigate("login") {
                    popUpTo(0)
                  }
                },
                onNavigateToPaywall = { navController.navigate("paywall") }
              )
            }
            composable("camera") {
              CameraScreen(
                viewModel = viewModel,
                onNavigateToAnalysis = { navController.navigate("analysis") },
                onBack = { navController.popBackStack() }
              )
            }
            composable("analysis") {
              AnalysisScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPaywall = { reason ->
                  navController.navigate("paywall?reason=${reason ?: ""}")
                },
                onMealSaved = { mealId ->
                  // Resultado consumido pelo ChatScreen quando a foto foi anexada
                  // por lá; inofensivo quando a origem é a Home (ninguém escuta).
                  navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("justSavedMealId", mealId)
                }
              )
            }
            composable("manual_entry") {
              ManualEntryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
              )
            }
            composable("chat") {
              ChatScreen(
                mainViewModel = viewModel,
                navController = navController,
                onBack = { navController.popBackStack() },
                onNavigateToCamera = { navController.navigate("camera") },
                onNavigateToAnalysis = { navController.navigate("analysis") },
                onNavigateToPaywall = { navController.navigate("paywall?reason=chat_locked") }
              )
            }
            composable(
              route = "edit_meal/{mealId}",
              arguments = listOf(navArgument("mealId") { type = NavType.LongType })
            ) { backStackEntry ->
              val mealId = backStackEntry.arguments?.getLong("mealId") ?: 0L
              EditMealScreen(
                mealId = mealId,
                onBack = { navController.popBackStack() }
              )
            }
            composable(
              route = "paywall?reason={reason}",
              arguments = listOf(navArgument("reason") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
              })
            ) { backStackEntry ->
              val reason = backStackEntry.arguments?.getString("reason")?.ifBlank { null }
              PaywallScreen(
                viewModel = viewModel,
                reason = reason,
                onBack = { navController.popBackStack() }
              )
            }
          }
        }
      }
    }
  }
}
