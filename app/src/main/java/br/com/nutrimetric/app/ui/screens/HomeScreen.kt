package br.com.nutrimetric.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import br.com.nutrimetric.app.data.local.MealEntity
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import br.com.nutrimetric.app.ui.state.MealUiState
import br.com.nutrimetric.app.ui.state.AlimentoUiState
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToBarcode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToEditMeal: (Long) -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToWeeklyReport: () -> Unit
) {
    val context = LocalContext.current
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()

    LaunchedEffect(onboardingCompleted) {
        if (!onboardingCompleted) {
            onNavigateToOnboarding()
        }
    }

    val todayMeals by viewModel.todayMealsUiState.collectAsState()
    val todayTotals by viewModel.todayTotals.collectAsState()
    val dailyCalorieGoal by viewModel.dailyCalorieGoal.collectAsState()
    val nutritionGoals by viewModel.nutritionGoals.collectAsState()
    val waterTotalMl by viewModel.waterTotalMl.collectAsState()
    val latestWeight by viewModel.latestWeight.collectAsState()
    val recentWeights by viewModel.recentWeights.collectAsState()
    val currentStreak by viewModel.currentStreak.collectAsState()
    val favoriteMeals by viewModel.favoriteMeals.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.setCurrentImageUri(uri)
                onNavigateToAnalysis()
            }
        }
    )

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Adicionar Refeição",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBottomSheet = false
                            onNavigateToCamera()
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Câmera",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Tirar foto com a câmera",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBottomSheet = false
                            galleryLauncher.launch("image/*")
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Galeria",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Escolher da galeria",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBottomSheet = false
                            onNavigateToManualEntry()
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Manual",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Registrar manualmente",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBottomSheet = false
                            onNavigateToBarcode()
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Código de Barras",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Escanear Código de Barras",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PratoBr", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (currentStreak > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.testTag("streak_badge")
                        ) {
                            Text(
                                text = "🔥 $currentStreak",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateToChat,
                        modifier = Modifier.testTag("chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Conversar com a Nutri"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToWeeklyReport,
                        modifier = Modifier.testTag("weekly_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Relatório Semanal"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Histórico"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary option to add meals (Gallery / Manual Entry)
                SmallFloatingActionButton(
                    onClick = {
                        showBottomSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("add_options_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Mais opções")
                }

                // Primary Direct Camera Trigger Button
                ExtendedFloatingActionButton(
                    text = { Text("Tirar Foto", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Câmera") },
                    onClick = {
                        onNavigateToCamera()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("direct_camera_fab")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Date Selection and History Navigation
            val selectedDateStr by viewModel.selectedDate.collectAsState()
            
            val displayDate = remember(selectedDateStr) {
                try {
                    val date = java.time.LocalDate.parse(selectedDateStr)
                    val today = java.time.LocalDate.now()
                    val yesterday = today.minusDays(1)
                    
                    when (date) {
                        today -> "Hoje"
                        yesterday -> "Ontem"
                        else -> {
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale("pt", "BR"))
                            date.format(formatter).replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                        }
                    }
                } catch (e: Exception) {
                    selectedDateStr
                }
            }

            var showDatePicker by remember { mutableStateOf(false) }
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = try {
                        val localDate = java.time.LocalDate.parse(selectedDateStr)
                        localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch(e: Exception) {
                        System.currentTimeMillis()
                    }
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val instant = java.time.Instant.ofEpochMilli(millis)
                                    val zoneId = java.time.ZoneId.of("UTC")
                                    val localDate = instant.atZone(zoneId).toLocalDate()
                                    viewModel.selectDate(localDate.toString())
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("Confirmar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancelar")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Generate list of 7 days for the active week
            val daysOfWeek = remember(selectedDateStr) {
                try {
                    val activeDate = java.time.LocalDate.parse(selectedDateStr)
                    val monday = activeDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    (0..6).map { monday.plusDays(it.toLong()) }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // Date Switcher Row (Previous, Calendar Picker, Next)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.selectPreviousDay() },
                        modifier = Modifier.testTag("prev_day_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Dia Anterior"
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Selecionar Data",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.selectNextDay() },
                        modifier = Modifier.testTag("next_day_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Próximo Dia"
                        )
                    }
                }
            }

            // Weekly Strip (Horizontal Calendar)
            if (daysOfWeek.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    daysOfWeek.forEach { day ->
                        val isSelected = day.toString() == selectedDateStr
                        val isToday = day == java.time.LocalDate.now()
                        
                        val dayBgColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else if (isToday) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                        
                        val dayTextColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        
                        val weekdayName = when (day.dayOfWeek) {
                            java.time.DayOfWeek.MONDAY -> "Seg"
                            java.time.DayOfWeek.TUESDAY -> "Ter"
                            java.time.DayOfWeek.WEDNESDAY -> "Qua"
                            java.time.DayOfWeek.THURSDAY -> "Qui"
                            java.time.DayOfWeek.FRIDAY -> "Sex"
                            java.time.DayOfWeek.SATURDAY -> "Sáb"
                            java.time.DayOfWeek.SUNDAY -> "Dom"
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .background(dayBgColor, shape = RoundedCornerShape(12.dp))
                                .clickable { viewModel.selectDate(day.toString()) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = weekdayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) dayTextColor.copy(alpha = 0.9f) else dayTextColor.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = dayTextColor
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DashboardProgress(
                        todayTotals = todayTotals,
                        nutritionGoals = nutritionGoals,
                        selectedDateStr = selectedDateStr,
                        displayDate = displayDate,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                item {
                    WaterCard(
                        currentMl = waterTotalMl,
                        goalMl = nutritionGoals.waterMl,
                        onAdd = { viewModel.addWater(it) },
                        onUndo = { viewModel.undoLastWater() }
                    )
                }
                item {
                    WeightCard(
                        latest = latestWeight,
                        recent = recentWeights,
                        onSave = { viewModel.saveWeight(it) }
                    )
                }
                if (favoriteMeals.isNotEmpty()) {
                    item {
                        FavoritesRow(
                            favorites = favoriteMeals,
                            onAddToday = { viewModel.repeatMealToday(it) }
                        )
                    }
                }
                if (todayMeals.isEmpty()) {
                    item { EmptyMealsCard(onNavigateToCamera = onNavigateToCamera) }
                } else {
                    items(todayMeals) { meal ->
                        MealCard(
                            meal = meal,
                            onDelete = { viewModel.deleteMeal(meal.id) },
                            onEdit = { onNavigateToEditMeal(meal.id) },
                            onToggleFavorite = { fav -> viewModel.toggleFavorite(meal.id, fav) },
                            onRepeat = { viewModel.repeatMealToday(meal.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesRow(
    favorites: List<br.com.nutrimetric.app.ui.state.FavoriteMealUiState>,
    onAddToday: (Long) -> Unit
) {
    Column {
        Text(
            text = "⭐ Favoritos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(favorites, key = { it.id }) { fav ->
                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .clickable { onAddToday(fav.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = fav.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = fav.formattedKcal,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adicionar hoje",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MealCard(
    meal: MealUiState,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onRepeat: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Excluir Refeição") },
            text = { Text("Tem certeza que deseja excluir esta refeição?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    onDelete()
                }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (meal.imagePath.isNotEmpty()) {
                AsyncImage(
                    model = meal.imagePath,
                    contentDescription = "Foto da refeição",
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = "Refeição Manual",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${meal.formattedTime} - ${meal.mealType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Mostrar menos" else "Mostrar mais",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = meal.formattedKcal,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MacroMiniBadge(
                        label = "P",
                        value = meal.formattedProtein,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    MacroMiniBadge(
                        label = "C",
                        value = meal.formattedCarbo,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    MacroMiniBadge(
                        label = "G",
                        value = meal.formattedGordura,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        if (meal.foods.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(6.dp))
                            meal.foods.forEach { alimento ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = alimento.nome,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = alimento.formattedKcal,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = alimento.formattedGramas,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        Text("P: ${alimento.formattedProtein}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        Text("C: ${alimento.formattedCarbo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        Text("G: ${alimento.formattedGordura}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Editar refeição",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onToggleFavorite(!meal.isFavorite) }) {
                    Icon(
                        imageVector = if (meal.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (meal.isFavorite) "Remover dos favoritos" else "Favoritar",
                        tint = if (meal.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        Icons.Filled.Replay,
                        contentDescription = "Repetir refeição hoje",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Deletar refeição",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun WaterCard(
    currentMl: Int,
    goalMl: Int,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit
) {
    val progress = if (goalMl > 0) (currentMl.toFloat() / goalMl.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💧 Água", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$currentMl / $goalMl ml",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { onAdd(200) },
                    modifier = Modifier.weight(1f).testTag("water_add_glass")
                ) { Text("+ Copo 200ml") }
                FilledTonalButton(
                    onClick = { onAdd(500) },
                    modifier = Modifier.weight(1f).testTag("water_add_bottle")
                ) { Text("+ Garrafa 500ml") }
                IconButton(onClick = onUndo, modifier = Modifier.testTag("water_undo")) {
                    Icon(Icons.Default.Delete, contentDescription = "Desfazer último")
                }
            }
        }
    }
}

@Composable
fun WeightCard(
    latest: br.com.nutrimetric.app.data.local.WeightEntity?,
    recent: List<br.com.nutrimetric.app.data.local.WeightEntity>,
    onSave: (Double) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    // Variação vs o registro anterior (recent vem ordenado do mais recente ao mais antigo)
    val delta: Double? = if (recent.size >= 2) recent[0].weightKg - recent[1].weightKg else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("⚖️ Peso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                if (latest != null) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f kg", latest.weightKg),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (delta != null && delta != 0.0) {
                        val sign = if (delta > 0) "+" else ""
                        val color = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Text(
                            text = String.format(java.util.Locale.US, "%s%.1f kg desde o último", sign, delta),
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    }
                } else {
                    Text(
                        "Nenhum registro ainda",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = { showDialog = true }, modifier = Modifier.testTag("weight_register")) {
                Text("Registrar")
            }
        }
    }

    if (showDialog) {
        var input by remember { mutableStateOf(latest?.weightKg?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Registrar peso") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.replace(',', '.') },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("weight_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        input.toDoubleOrNull()?.let { if (it > 0) onSave(it) }
                        showDialog = false
                    }
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun EmptyMealsCard(onNavigateToCamera: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Nenhuma refeição hoje",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tire uma foto do seu prato para analisar automaticamente as calorias e macronutrientes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onNavigateToCamera,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier.testTag("empty_state_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tirar Foto do Prato", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MacroMiniBadge(
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun DashboardProgress(
    todayTotals: br.com.nutrimetric.app.repository.DailyTotals?,
    nutritionGoals: br.com.nutrimetric.app.repository.NutritionGoals,
    selectedDateStr: String,
    displayDate: String,
    onNavigateToSettings: () -> Unit
) {
    val currentKcal = ((todayTotals?.milliKcal ?: 0) / 1000).toInt()
    val dailyCalorieGoal = nutritionGoals.calories
    val progress = if (dailyCalorieGoal > 0) currentKcal.toFloat() / dailyCalorieGoal.toFloat() else 0f
    val remainingKcal = dailyCalorieGoal - currentKcal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedDateStr == java.time.LocalDate.now().toString()) "Hoje" else displayDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.clickable { onNavigateToSettings() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar Meta",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Meta: $dailyCalorieGoal kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "$currentKcal / $dailyCalorieGoal kcal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (currentKcal > dailyCalorieGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
        LinearProgressIndicator(
            progress = { progress.coerceAtMost(1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (currentKcal > dailyCalorieGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (remainingKcal >= 0) {
                    "Faltam $remainingKcal kcal para atingir sua meta."
                } else {
                    "Meta excedida em ${-remainingKcal} kcal!"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (remainingKcal >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Proteínas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val pCurrent = ((todayTotals?.milliProtein ?: 0) / 1000).toInt()
                    val pGoal = nutritionGoals.protein
                    val pProgress = if (pGoal > 0) pCurrent.toFloat() / pGoal else 0f
                    Text("$pCurrent / ${pGoal}g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pProgress.coerceAtMost(1f) },
                        modifier = Modifier.width(60.dp).height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Carboidratos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val cCurrent = ((todayTotals?.milliCarbs ?: 0) / 1000).toInt()
                    val cGoal = nutritionGoals.carbs
                    val cProgress = if (cGoal > 0) cCurrent.toFloat() / cGoal else 0f
                    Text("$cCurrent / ${cGoal}g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { cProgress.coerceAtMost(1f) },
                        modifier = Modifier.width(60.dp).height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Gorduras", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val fCurrent = ((todayTotals?.milliFat ?: 0) / 1000).toInt()
                    val fGoal = nutritionGoals.fat
                    val fProgress = if (fGoal > 0) fCurrent.toFloat() / fGoal else 0f
                    Text("$fCurrent / ${fGoal}g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fProgress.coerceAtMost(1f) },
                        modifier = Modifier.width(60.dp).height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}
