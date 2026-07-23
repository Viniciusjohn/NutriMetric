package br.com.nutrimetric.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.nutrimetric.app.repository.AuthRepository
import br.com.nutrimetric.app.repository.ReminderSettings
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://viniciusjohn.github.io/NutriMetric/privacy-policy.html"
private const val TERMS_URL = "https://viniciusjohn.github.io/NutriMetric/terms.html"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    val nutritionGoals by viewModel.nutritionGoals.collectAsState()
    val reminder by viewModel.reminderSettings.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

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

                if (!isPremium) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPaywall() }
                            .testTag("premium_upsell_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "NutriMetric Premium",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "15 fotos por dia, Nutri IA e relatórios semanais",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Botão de Premium de TESTE — visível só pra contas autorizadas
                // (checagem no cliente é só de visibilidade; a Cloud Function
                // re-valida a allowlist no servidor).
                if (viewModel.isTestPremiumAllowed()) {
                    var isTogglingTestPremium by remember { mutableStateOf(false) }
                    var testPremiumError by remember { mutableStateOf<String?>(null) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🧪 Premium de teste", fontWeight = FontWeight.Bold)
                            Text(
                                if (isPremium) "Premium ATIVO (teste). Toque para desativar."
                                else "Libera chat e relatório premium só pra você testar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    testPremiumError = null
                                    isTogglingTestPremium = true
                                    scope.launch {
                                        val result = viewModel.setTestPremium(!isPremium)
                                        isTogglingTestPremium = false
                                        result.onFailure { e ->
                                            testPremiumError = "Falhou: ${e.message}"
                                        }
                                    }
                                },
                                enabled = !isTogglingTestPremium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isPremium) "Desativar Premium (teste)" else "Ativar Premium (teste)")
                            }
                            testPremiumError?.let {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
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

                // 7. Conta (logout, exclusão, documentos legais)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Conta",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("privacy_policy_link")
                        ) {
                            Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Política de Privacidade", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("terms_link")
                        ) {
                            Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Termos de Uso", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        TextButton(
                            onClick = {
                                authRepository.signOut()
                                onSignedOut()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("logout_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sair", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                        TextButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_account_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Excluir Conta", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                        deleteError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteConfirmDialog = false },
            title = { Text("Excluir conta") },
            text = {
                Text(
                    "Isso apaga permanentemente sua conta e todos os seus dados (refeições, metas, histórico). " +
                        "Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = {
                        isDeletingAccount = true
                        deleteError = null
                        scope.launch {
                            val result = authRepository.deleteAccount()
                            isDeletingAccount = false
                            result.fold(
                                onSuccess = {
                                    showDeleteConfirmDialog = false
                                    onSignedOut()
                                },
                                onFailure = { e ->
                                    deleteError = "Não foi possível excluir a conta: ${e.message}"
                                }
                            )
                        }
                    }
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
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
