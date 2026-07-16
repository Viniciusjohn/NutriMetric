package br.com.nutrimetric.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val historyTotals by viewModel.historyTotals.collectAsState()
    val dailyCalorieGoal by viewModel.dailyCalorieGoal.collectAsState()
    val nutritionGoals by viewModel.nutritionGoals.collectAsState()
    val selectedDateStr by viewModel.selectedDate.collectAsState()
    val todayMeals by viewModel.todayMealsUiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }

    // Convert history list to map for fast date-based lookup
    val totalsMap = remember(historyTotals) {
        historyTotals.associateBy { it.date }
    }

    val selectedDate = remember(selectedDateStr) {
        try {
            LocalDate.parse(selectedDateStr)
        } catch (e: Exception) {
            LocalDate.now()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico e Evolução", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calendar Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Month Selector Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { currentYearMonth = currentYearMonth.minusMonths(1) },
                                modifier = Modifier.testTag("prev_month_button")
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Mês Anterior")
                            }

                            val monthName = currentYearMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
                                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                            Text(
                                text = "$monthName ${currentYearMonth.year}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = { currentYearMonth = currentYearMonth.plusMonths(1) },
                                modifier = Modifier.testTag("next_month_button")
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Próximo Mês")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Weekdays Row
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val daysOfWeek = listOf("D", "S", "T", "Q", "Q", "S", "S")
                            daysOfWeek.forEach { day ->
                                Text(
                                    text = day,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar Grid Days
                        val firstDayOfMonth = currentYearMonth.atDay(1)
                        val daysInMonth = currentYearMonth.lengthOfMonth()
                        val firstDayOfWeekValue = firstDayOfMonth.dayOfWeek.value % 7 // 0 for Sunday, 1 for Monday, etc.

                        var currentDay = 1
                        val totalCells = daysInMonth + firstDayOfWeekValue
                        val rows = (totalCells + 6) / 7

                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (col in 0..6) {
                                    val cellIndex = row * 7 + col
                                    if (cellIndex < firstDayOfWeekValue || currentDay > daysInMonth) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    } else {
                                        val dayDate = currentYearMonth.atDay(currentDay)
                                        val dateString = dayDate.toString()
                                        val totalForDay = totalsMap[dateString]
                                        
                                        val isSelected = dayDate == selectedDate
                                        val isToday = dayDate == LocalDate.now()

                                        // Calculate calorie goal completion percentage to color-code
                                        val calorieCompletion = if (totalForDay != null && dailyCalorieGoal > 0) {
                                            totalForDay.kcal / dailyCalorieGoal
                                        } else {
                                            0.0
                                        }

                                        val (bgColor, textColor) = when {
                                            isSelected -> Pair(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.onPrimary
                                            )
                                            calorieCompletion > 0.0 -> {
                                                val intensity = calorieCompletion.coerceIn(0.1, 1.2)
                                                val baseColor = if (intensity > 1.0) {
                                                    // Exceeded goal slightly
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    // Met or approaching goal
                                                    MaterialTheme.colorScheme.primaryContainer
                                                }
                                                val textCol = if (intensity > 1.0) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                }
                                                Pair(baseColor, textCol)
                                            }
                                            isToday -> Pair(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                MaterialTheme.colorScheme.primary
                                            )
                                            else -> Pair(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(bgColor)
                                                .clickable {
                                                    viewModel.selectDate(dateString)
                                                }
                                                .testTag("calendar_day_$currentDay"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = currentDay.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = textColor
                                                )
                                                // Small dot indicating meal logged
                                                if (totalForDay != null && !isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (calorieCompletion > 1.0) MaterialTheme.colorScheme.error 
                                                                else MaterialTheme.colorScheme.primary
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                        currentDay++
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selection Details and Macro Breakdown Section
            item {
                val selectedDayTotals = totalsMap[selectedDateStr]
                val consumedKcal = selectedDayTotals?.kcal?.toInt() ?: 0
                val progress = if (dailyCalorieGoal > 0) consumedKcal.toFloat() / dailyCalorieGoal else 0f
                val remainingKcal = dailyCalorieGoal - consumedKcal

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Date Heading
                        val dateDisplay = try {
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR"))
                            selectedDate.format(formatter).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        } catch (e: Exception) {
                            selectedDateStr
                        }

                        Text(
                            text = dateDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Calorie Progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "$consumedKcal kcal",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (consumedKcal > dailyCalorieGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Consumido de $dailyCalorieGoal kcal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (remainingKcal >= 0) "$remainingKcal kcal" else "${-remainingKcal} kcal excedido",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingKcal >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = if (remainingKcal >= 0) "Restantes" else "Excedidos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (consumedKcal > dailyCalorieGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Macros Row
                        val pGoal = nutritionGoals.protein
                        val cGoal = nutritionGoals.carbs
                        val fGoal = nutritionGoals.fat

                        val pActual = selectedDayTotals?.protein?.toInt() ?: 0
                        val cActual = selectedDayTotals?.carbohydrate?.toInt() ?: 0
                        val fActual = selectedDayTotals?.lipid?.toInt() ?: 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MacroBarProgress(
                                label = "Proteínas",
                                value = pActual,
                                goal = pGoal,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.weight(1f)
                            )
                            MacroBarProgress(
                                label = "Carbos",
                                value = cActual,
                                goal = cGoal,
                                color = Color(0xFFFF9800),
                                modifier = Modifier.weight(1f)
                            )
                            MacroBarProgress(
                                label = "Gorduras",
                                value = fActual,
                                goal = fGoal,
                                color = Color(0xFFE91E63),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Weekly Trend Evolution Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Evolução",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tendência nos Últimos 7 Dias",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Vico chart logic for last 7 days
                        val last7Days = remember {
                            (0..6).map { LocalDate.now().minusDays((6 - it).toLong()) }
                        }

                        val entries = remember(last7Days, totalsMap) {
                            last7Days.mapIndexed { index, day ->
                                val dayStr = day.toString()
                                val kcal = totalsMap[dayStr]?.kcal?.toFloat() ?: 0f
                                com.patrykandpatrick.vico.core.entry.entryOf(index.toFloat(), kcal)
                            }
                        }

                        val entryModel = remember(entries) { entryModelOf(entries) }
                        val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                            val index = value.toInt()
                            if (index in last7Days.indices) {
                                val day = last7Days[index]
                                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                            } else {
                                ""
                            }
                        }

                        Chart(
                            chart = columnChart(),
                            model = entryModel,
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisValueFormatter),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(top = 16.dp)
                        )
                    }
                }
            }

            // Logged Meals Header for selected day
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Refeições do Dia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "${todayMeals.size} registradas",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (todayMeals.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nenhuma refeição registrada para esta data.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(todayMeals, key = { it.id }) { meal ->
                    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                showDeleteConfirmDialog = true
                                true
                            } else {
                                false
                            }
                        }
                    )

                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { 
                                showDeleteConfirmDialog = false 
                                coroutineScope.launch { dismissState.reset() }
                            },
                            title = { Text("Excluir Refeição") },
                            text = { Text("Tem certeza que deseja excluir esta refeição?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirmDialog = false
                                    viewModel.deleteMeal(meal.id)
                                }) {
                                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDeleteConfirmDialog = false
                                    coroutineScope.launch { dismissState.reset() }
                                }) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color, shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remover",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { /* Opcional: ver detalhes se houver */ },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = meal.mealType,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = meal.formattedTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = meal.formattedKcal,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "P: ${meal.formattedProtein} | C: ${meal.formattedCarbo}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroBarProgress(
    label: String,
    value: Int,
    goal: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fraction = if (goal > 0) value.toFloat() / goal else 0f
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${value}g",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "meta: ${goal}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
        }
    }
}
