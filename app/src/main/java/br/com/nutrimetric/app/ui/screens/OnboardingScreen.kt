package br.com.nutrimetric.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.nutrimetric.app.repository.UserProfile
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import br.com.nutrimetric.app.utils.NutritionCalculator
import kotlinx.coroutines.launch

private enum class OnboardingStep {
    WEIGHT, HEIGHT, AGE, SEX, ACTIVITY, GOAL, SUMMARY
}

private data class Bubble(val fromNutri: Boolean, val text: String)

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onFinished: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.WEIGHT) }
    val bubbles = remember { mutableStateListOf<Bubble>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var weightKg by remember { mutableStateOf<Double?>(null) }
    var heightCm by remember { mutableStateOf<Int?>(null) }
    var age by remember { mutableStateOf<Int?>(null) }
    var sex by remember { mutableStateOf<String?>(null) }
    var activityLevel by remember { mutableStateOf<String?>(null) }
    var goal by remember { mutableStateOf<String?>(null) }
    var consentChecked by remember { mutableStateOf(false) }

    fun askNext(text: String) {
        bubbles.add(Bubble(fromNutri = true, text = text))
    }

    fun answer(displayText: String) {
        bubbles.add(Bubble(fromNutri = false, text = displayText))
    }

    fun scrollToEnd() {
        scope.launch { listState.animateScrollToItem((bubbles.size - 1).coerceAtLeast(0)) }
    }

    LaunchedEffect(Unit) {
        askNext("Oi! Eu sou a Nutri 👋 Vou te fazer algumas perguntas rápidas pra calcular suas metas de calorias e macros. Bora começar?")
        askNext("Qual é o seu peso atual (em kg)?")
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Vamos nos conhecer", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bubbles) { bubble -> ChatBubble(bubble) }

                if (step == OnboardingStep.SUMMARY) {
                    item {
                        SummaryCard(
                            weightKg = weightKg ?: 0.0,
                            heightCm = heightCm ?: 0,
                            age = age ?: 0,
                            sex = sex ?: "F",
                            activityLevel = activityLevel ?: "moderate",
                            goal = goal ?: "maintain",
                            consentChecked = consentChecked,
                            onConsentChange = { consentChecked = it },
                            onConfirm = {
                                viewModel.completeOnboarding(
                                    profile = UserProfile(
                                        heightCm = heightCm ?: 170,
                                        age = age ?: 30,
                                        sex = sex ?: "F",
                                        activityLevel = activityLevel ?: "moderate",
                                        goal = goal ?: "maintain",
                                        onboardingCompleted = true
                                    ),
                                    weightKg = weightKg ?: 70.0
                                )
                                onFinished()
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = step != OnboardingStep.SUMMARY) {
                Surface(shadowElevation = 8.dp) {
                    when (step) {
                        OnboardingStep.WEIGHT -> NumberInputRow(
                            label = "Peso (kg)",
                            onSubmit = { value ->
                                if (value == null || value <= 0) return@NumberInputRow
                                weightKg = value
                                answer("${value.toInt()} kg")
                                askNext("Qual é a sua altura (em cm)?")
                                step = OnboardingStep.HEIGHT
                                scrollToEnd()
                            }
                        )
                        OnboardingStep.HEIGHT -> NumberInputRow(
                            label = "Altura (cm)",
                            onSubmit = { value ->
                                if (value == null || value <= 0) return@NumberInputRow
                                heightCm = value.toInt()
                                answer("${value.toInt()} cm")
                                askNext("Qual é a sua idade?")
                                step = OnboardingStep.AGE
                                scrollToEnd()
                            }
                        )
                        OnboardingStep.AGE -> NumberInputRow(
                            label = "Idade",
                            onSubmit = { value ->
                                if (value == null || value <= 0) return@NumberInputRow
                                age = value.toInt()
                                answer("${value.toInt()} anos")
                                askNext("Qual é o seu sexo biológico? (usado só pra calcular sua taxa metabólica)")
                                step = OnboardingStep.SEX
                                scrollToEnd()
                            }
                        )
                        OnboardingStep.SEX -> ChoiceRow(
                            options = listOf("Feminino" to "F", "Masculino" to "M"),
                            onSelect = { label, value ->
                                sex = value
                                answer(label)
                                askNext("Como é seu nível de atividade física no dia a dia?")
                                step = OnboardingStep.ACTIVITY
                                scrollToEnd()
                            }
                        )
                        OnboardingStep.ACTIVITY -> ChoiceRow(
                            options = listOf(
                                "Sedentário (pouco ou nenhum exercício)" to "sedentary",
                                "Leve (1-3x/semana)" to "light",
                                "Moderado (3-5x/semana)" to "moderate",
                                "Intenso (6-7x/semana)" to "active",
                                "Muito intenso (atleta/trabalho físico)" to "very_active"
                            ),
                            onSelect = { label, value ->
                                activityLevel = value
                                answer(label)
                                askNext("Qual é o seu objetivo principal agora?")
                                step = OnboardingStep.GOAL
                                scrollToEnd()
                            }
                        )
                        OnboardingStep.GOAL -> ChoiceRow(
                            options = listOf(
                                "Perder peso" to "lose",
                                "Manter peso" to "maintain",
                                "Ganhar peso/massa" to "gain"
                            ),
                            onSelect = { label, value ->
                                goal = value
                                answer(label)
                                askNext("Perfeito! Calculei suas metas com base nesses dados. Dá uma olhada:")
                                step = OnboardingStep.SUMMARY
                                scrollToEnd()
                            }
                        )
                        OnboardingStep.SUMMARY -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(bubble: Bubble) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (bubble.fromNutri) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (bubble.fromNutri) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = bubble.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (bubble.fromNutri) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
        }
    }
}

@Composable
private fun NumberInputRow(label: String, onSubmit: (Double?) -> Unit) {
    var text by remember(label) { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                val value = text.toDoubleOrNull()
                onSubmit(value)
                text = ""
            },
            enabled = text.toDoubleOrNull() != null
        ) {
            Text("Enviar")
        }
    }
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, onSelect: (label: String, value: String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            OutlinedButton(
                onClick = { onSelect(label, value) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SummaryCard(
    weightKg: Double,
    heightCm: Int,
    age: Int,
    sex: String,
    activityLevel: String,
    goal: String,
    consentChecked: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    val activityEnum = when (activityLevel) {
        "sedentary" -> NutritionCalculator.ActivityLevel.SEDENTARY
        "light" -> NutritionCalculator.ActivityLevel.LIGHT
        "active" -> NutritionCalculator.ActivityLevel.ACTIVE
        "very_active" -> NutritionCalculator.ActivityLevel.VERY_ACTIVE
        else -> NutritionCalculator.ActivityLevel.MODERATE
    }
    val goalEnum = when (goal) {
        "lose" -> NutritionCalculator.Goal.LOSE
        "gain" -> NutritionCalculator.Goal.GAIN
        else -> NutritionCalculator.Goal.MAINTAIN
    }
    val sexEnum = if (sex == "M") NutritionCalculator.Sex.MALE else NutritionCalculator.Sex.FEMALE

    val goals = remember(weightKg, heightCm, age, sex, activityLevel, goal) {
        NutritionCalculator.calculateGoals(weightKg, heightCm.toDouble(), age, sexEnum, activityEnum, goalEnum)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Suas metas diárias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("${goals.calories} kcal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Proteína: ${goals.protein}g · Carboidratos: ${goals.carbs}g · Gordura: ${goals.fat}g · Água: ${goals.waterMl}ml")
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Essas metas são uma estimativa (fórmula de Mifflin-St Jeor) e não substituem a avaliação de um nutricionista.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consentChecked, onCheckedChange = onConsentChange)
                Text(
                    text = "Concordo com o uso destes dados de saúde para calcular minhas metas e personalizar o app (LGPD).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConfirm,
                enabled = consentChecked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar e Começar")
            }
        }
    }
}
