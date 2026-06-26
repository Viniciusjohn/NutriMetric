package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AnalysisScreen
import com.example.ui.screens.BarcodeScannerScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ManualEntryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

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

          NavHost(navController = navController, startDestination = "home") {
            composable("home") {
              HomeScreen(
                viewModel = viewModel,
                onNavigateToAnalysis = { navController.navigate("analysis") },
                onNavigateToCamera = { navController.navigate("camera") },
                onNavigateToManualEntry = { navController.navigate("manual_entry") },
                onNavigateToBarcode = { navController.navigate("barcode") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("history") }
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
                onBack = { navController.popBackStack() }
              )
            }
            composable("settings") {
              SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() }
              )
            }
            composable("manual_entry") {
              ManualEntryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
              )
            }
          }
        }
      }
    }
  }
}
