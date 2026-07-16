package br.com.nutrimetric.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.nutrimetric.app.repository.ReminderSettings
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val nutritionGoals by viewModel.nutritionGoals.collectAsState()
    val reminder by viewModel.reminderSettings.collectAsState()

    // Internal states initialized with current goals
    var calories by remember(nutritionGoals) { mutableStateOf(nutritionGoals.calories.toFloat()) }
    var protein by remember(nutritionGoals) { mutableStateOf(nutritionGoals.protein.toFloat()) }
    var carbs by remember(nutritionGoals) { mutableStateOf(nutritionGoals.carbs.toFloat()) }
    var fat by remember(nutritionGoals) { mutableStateOf(nutritionGoals.fat.toFloat()) }
    var water by remember(nutritionGoals) { mutableStateOf(nutritionGoals.waterMl.toFloat()) }

    var showSuccessSnackbar by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val notificationsPermission = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metas Nutricionais", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Configure suas Metas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Ajuste os valores abaixo para atualizar as barras de progresso na tela inicial.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 1. Calories Slider
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Meta de Calorias",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${calories.toInt()} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = calories,
                            onValueChange = { calories = it },
                            valueRange = 1000f..5000f,
                            steps = 79, // Steps of 50
                            modifier = Modifier.testTag("calories_slider")
                        )
                    }
                }

                // 2. Protein Slider
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Meta de Proteínas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${protein.toInt()}g",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = protein,
                            onValueChange = { protein = it },
                            valueRange = 20f..300f,
                            steps = 55, // Steps of 5
                            modifier = Modifier.testTag("protein_slider")
                        )
                    }
                }

                // 3. Carbohydrates Slider
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Meta de Carboidratos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${carbs.toInt()}g",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = carbs,
                            onValueChange = { carbs = it },
                            valueRange = 50f..600f,
                            steps = 109, // Steps of 5
                            modifier = Modifier.testTag("carbs_slider")
                        )
                    }
                }

                // 4. Fats Slider
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Meta de Gorduras",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${fat.toInt()}g",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = fat,
                            onValueChange = { fat = it },
                            valueRange = 10f..200f,
                            steps = 37, // Steps of 5
                            modifier = Modifier.testTag("fat_slider")
                        )
                    }
                }

                // 5. Water goal slider
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Meta de Água",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${water.toInt()} ml",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = water,
                            onValueChange = { water = it },
                            valueRange = 500f..5000f,
                            steps = 89, // Steps of 50ml
                            modifier = Modifier.testTag("water_slider")
                        )
                    }
                }

                // 6. Daily reminder
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Lembrete diário",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = reminder.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !notificationsPermission.status.isGranted) {
                                        notificationsPermission.launchPermissionRequest()
                                    }
                                    viewModel.saveReminder(reminder.copy(enabled = enabled))
                                },
                                modifier = Modifier.testTag("reminder_switch")
                            )
                        }
                        if (reminder.enabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Você será lembrado todos os dias às " +
                                    String.format(java.util.Locale.US, "%02d:%02d", reminder.hour, reminder.minute) + ".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.testTag("reminder_time_button")
                            ) {
                                Text("Alterar horário")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.saveGoals(
                            calories = calories.toInt(),
                            protein = protein.toInt(),
                            carbs = carbs.toInt(),
                            fat = fat.toInt()
                        )
                        viewModel.saveWaterGoal(water.toInt())
                        showSuccessSnackbar = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_goals_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Salvar Metas", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (showSuccessSnackbar) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showSuccessSnackbar = false }) {
                            Text("OK", color = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text("Metas salvas offline com sucesso!")
                }
                
                // Automatically auto-dismiss snackbar after a few seconds
                LaunchedEffect(showSuccessSnackbar) {
                    kotlinx.coroutines.delay(3000)
                    showSuccessSnackbar = false
                }
            }
        }
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            initialHour = reminder.hour,
            initialMinute = reminder.minute,
            onConfirm = { hour, minute ->
                viewModel.saveReminder(reminder.copy(enabled = true, hour = hour, minute = minute))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horário do lembrete") },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
