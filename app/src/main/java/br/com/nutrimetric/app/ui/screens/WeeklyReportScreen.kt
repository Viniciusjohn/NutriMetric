package br.com.nutrimetric.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.nutrimetric.app.data.local.DateDailyTotals
import br.com.nutrimetric.app.repository.NutritionGoals
import br.com.nutrimetric.app.ui.viewmodel.WeeklyReportViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    viewModel: WeeklyReportViewModel = viewModel()
) {
    val weeklyTotals by viewModel.weeklyTotals.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val aiSummary by viewModel.aiSummary.collectAsState()
    val isLoadingSummary by viewModel.isLoadingSummary.collectAsState()
    val summaryError by viewModel.summaryError.collectAsState()
    val premiumRequired by viewModel.premiumRequired.collectAsState()

    LaunchedEffect(premiumRequired) {
        if (premiumRequired) {
            onNavigateToPaywall()
            viewModel.clearPremiumRequired()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatório Semanal", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { WeeklyChartCard(weeklyTotals, goals) }
            item { MacroAveragesCard(weeklyTotals, goals) }
            item {
                AiSummaryCard(
                    isPremium = isPremium,
                    isLoading = isLoadingSummary,
                    summary = aiSummary,
                    error = summaryError,
                    onGenerate = { viewModel.requestAiSummary() },
                    onUpsellClick = onNavigateToPaywall
                )
            }
        }
    }
}

@Composable
private fun WeeklyChartCard(weeklyTotals: List<DateDailyTotals>, goals: NutritionGoals) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Calorias nos últimos 7 dias",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Meta: ${goals.calories} kcal/dia",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (weeklyTotals.isEmpty()) {
                Text(
                    text = "Sem registros nos últimos 7 dias ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                return@Column
            }

            val entries = remember(weeklyTotals) {
                weeklyTotals.mapIndexed { index, day -> entryOf(index.toFloat(), day.kcal.toFloat()) }
            }
            val entryModel = remember(entries) { entryModelOf(entries) }
            val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                val index = value.toInt()
                weeklyTotals.getOrNull(index)?.date?.let { dateStr ->
                    runCatching { LocalDate.parse(dateStr) }.getOrNull()
                        ?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))
                        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                } ?: ""
            }

            Chart(
                chart = columnChart(),
                model = entryModel,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(valueFormatter = bottomAxisValueFormatter),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun MacroAveragesCard(weeklyTotals: List<DateDailyTotals>, goals: NutritionGoals) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Média diária vs. meta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (weeklyTotals.isEmpty()) {
                Text(
                    text = "Registre refeições para ver sua média semanal.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                return@Column
            }

            val days = weeklyTotals.size
            val avgKcal = weeklyTotals.sumOf { it.kcal } / days
            val avgProtein = weeklyTotals.sumOf { it.protein } / days
            val avgCarbs = weeklyTotals.sumOf { it.carbohydrate } / days
            val avgFat = weeklyTotals.sumOf { it.lipid } / days

            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroAverageRow("Calorias", avgKcal, goals.calories.toDouble(), "kcal")
                MacroAverageRow("Proteína", avgProtein, goals.protein.toDouble(), "g")
                MacroAverageRow("Carboidratos", avgCarbs, goals.carbs.toDouble(), "g")
                MacroAverageRow("Gordura", avgFat, goals.fat.toDouble(), "g")
            }
        }
    }
}

@Composable
private fun MacroAverageRow(label: String, average: Double, goal: Double, unit: String) {
    val percent = if (goal > 0) ((average / goal) * 100).roundToInt() else 0
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${average.roundToInt()}$unit / ${goal.roundToInt()}$unit ($percent%)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
private fun AiSummaryCard(
    isPremium: Boolean,
    isLoading: Boolean,
    summary: String?,
    error: String?,
    onGenerate: () -> Unit,
    onUpsellClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPremium) Icons.Filled.AutoAwesome else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Análise da Nutri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isPremium) {
                Text(
                    text = "Assine o Premium para receber uma análise escrita da sua semana, com destaques e sugestões personalizadas.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onUpsellClick) {
                    Text("Ver planos Premium")
                }
                return@Column
            }

            when {
                isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Gerando sua análise...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                summary != null -> {
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onGenerate) {
                        Text("Gerar de novo")
                    }
                }
                else -> {
                    Text(
                        text = "Toque para receber uma análise escrita da sua semana pela Nutri.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onGenerate) {
                        Text("Gerar análise da semana")
                    }
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
