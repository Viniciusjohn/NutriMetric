package br.com.nutrimetric.app.ui.screens

import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import br.com.nutrimetric.app.ui.viewmodel.AnalysisState
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uri by viewModel.currentImageUri.collectAsState()
    val state by viewModel.analysisState.collectAsState()

    var showAddItemDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uri) {
        if (uri != null && state is AnalysisState.Idle) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri!!)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri!!)
            }
            viewModel.analyzeImage(bitmap, uri.toString())
        }
    }

    if (state is AnalysisState.Success) {
        val currentState = state as AnalysisState.Success
        PlateReviewScreen(
            imageBase64 = currentState.imageBase64,
            imageUri = currentState.uri,
            initialItems = currentState.mappedItems,
            onBack = {
                viewModel.resetAnalysis()
                onBack()
            },
            onConfirmSuccess = {
                viewModel.resetAnalysis()
                onBack()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análise do Prato") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetAnalysis()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            uri?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Prato",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            when (val currentState = state) {
                is AnalysisState.Idle -> {
                    // Esperando iniciar
                }
                is AnalysisState.Loading -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("A IA está analisando seu prato brasileiro...", style = MaterialTheme.typography.bodyLarge)
                }
                is AnalysisState.Error -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Erro: ${currentState.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(onClick = {
                        viewModel.resetAnalysis()
                        onBack()
                    }) {
                        Text("Tentar Novamente")
                    }
                }
                is AnalysisState.Success -> {
                    val mappedItems = currentState.mappedItems
                    val totalKcal = mappedItems.sumOf { it.milliKcal } / 1000
                    val totalProteina = mappedItems.sumOf { it.milliProtein } / 1000
                    val totalCarbo = mappedItems.sumOf { it.milliCarbs } / 1000
                    val totalGordura = mappedItems.sumOf { it.milliFat } / 1000

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Alimentos Identificados",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            mappedItems.forEachIndexed { index, mappedItem ->
                                val tacoFood = mappedItem.tacoFood
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = tacoFood?.description ?: mappedItem.genericName, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (tacoFood != null) "Porção padrão (100g)" else "Item não encontrado na TACO",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { editingIndex = index }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar alimento",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (tacoFood != null && mappedItem.manualKcal == null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(
                                                onClick = { viewModel.updateMappedItemQuantity(index, mappedItem.halfPortions - 1) },
                                                enabled = mappedItem.halfPortions > 0,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("- 0.5")
                                            }
                                            
                                            Text(
                                                text = "${mappedItem.halfPortions / 2.0}",
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            FilledTonalButton(
                                                onClick = { viewModel.updateMappedItemQuantity(index, mappedItem.halfPortions + 1) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("+ 0.5")
                                            }
                                        }
                                    } else {
                                        if (mappedItem.manualKcal != null) {
                                            Text(
                                                text = "Nutrientes Manuais: ${mappedItem.manualKcal} kcal • ${mappedItem.manualGrams ?: 0}g",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "P: ${mappedItem.manualProtein ?: 0}g • C: ${mappedItem.manualCarbs ?: 0}g • G: ${mappedItem.manualFat ?: 0}g",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Text(
                                                text = "Toque no lápis ao lado para definir kcal e macros",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }

                            Button(
                                onClick = { showAddItemDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Adicionar Outro Alimento Manual")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MacroItem("Calorias", "$totalKcal kcal")
                                MacroItem("Proteína", "${totalProteina}g")
                                MacroItem("Carb", "${totalCarbo}g")
                                MacroItem("Gordura", "${totalGordura}g")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetAnalysis()
                                onBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Descartar")
                        }
                        Button(
                            onClick = {
                                viewModel.saveMeal(currentState.imageBase64, currentState.uri, mappedItems)
                                onBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Salvar Refeição")
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Dialogs
    editingIndex?.let { index ->
        val currentState = state
        if (currentState is AnalysisState.Success) {
            val item = currentState.mappedItems[index]
            val taco = item.tacoFood
            EditFoodDialog(
                initialName = taco?.description ?: item.genericName,
                initialKcal = item.manualKcal ?: (taco?.energyKcal?.toLong()?.let { it * item.grams / 100 }),
                initialProtein = item.manualProtein ?: (taco?.protein?.toLong()?.let { it * item.grams / 100 }),
                initialCarbs = item.manualCarbs ?: (taco?.carbohydrate?.toLong()?.let { it * item.grams / 100 }),
                initialFat = item.manualFat ?: (taco?.lipid?.toLong()?.let { it * item.grams / 100 }),
                initialGrams = item.manualGrams ?: item.grams,
                onDismiss = { editingIndex = null },
                onConfirm = { name, kcal, protein, carbs, fat, grams ->
                    viewModel.updateMappedItemManualValues(index, kcal, protein, carbs, fat, grams)
                    editingIndex = null
                }
            )
        }
    }

    if (showAddItemDialog) {
        EditFoodDialog(
            initialName = "",
            initialKcal = null,
            initialProtein = null,
            initialCarbs = null,
            initialFat = null,
            initialGrams = null,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, kcal, protein, carbs, fat, grams ->
                viewModel.addManualFoodItem(name, kcal, protein, carbs, fat, grams)
                showAddItemDialog = false
            }
        )
    }
}

@Composable
fun EditFoodDialog(
    initialName: String,
    initialKcal: Long?,
    initialProtein: Long?,
    initialCarbs: Long?,
    initialFat: Long?,
    initialGrams: Int?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, kcal: Long, protein: Long, carbs: Long, fat: Long, grams: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var kcalStr by remember { mutableStateOf(initialKcal?.toString() ?: "") }
    var proteinStr by remember { mutableStateOf(initialProtein?.toString() ?: "") }
    var carbsStr by remember { mutableStateOf(initialCarbs?.toString() ?: "") }
    var fatStr by remember { mutableStateOf(initialFat?.toString() ?: "") }
    var gramsStr by remember { mutableStateOf(initialGrams?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Definir Nutrientes Manuais", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Alimento") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = kcalStr,
                    onValueChange = { kcalStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Calorias (kcal) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = gramsStr,
                    onValueChange = { gramsStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Quantidade (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = proteinStr,
                    onValueChange = { proteinStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Proteínas (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = carbsStr,
                    onValueChange = { carbsStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Carboidratos (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = fatStr,
                    onValueChange = { fatStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Gorduras (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        kcalStr.toLongOrNull() ?: 0L,
                        proteinStr.toLongOrNull() ?: 0L,
                        carbsStr.toLongOrNull() ?: 0L,
                        fatStr.toLongOrNull() ?: 0L,
                        gramsStr.toIntOrNull() ?: 0
                    )
                },
                enabled = name.isNotBlank() && kcalStr.isNotBlank()
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun MacroItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
