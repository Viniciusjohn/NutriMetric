package br.com.nutrimetric.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: MainViewModel,
    reason: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var offering by remember { mutableStateOf<Offering?>(null) }
    var isLoadingOffering by remember { mutableStateOf(true) }
    var isPurchasing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        offering = viewModel.getOffering()
        isLoadingOffering = false
    }

    val headline = when (reason) {
        "quota_exceeded" -> "Você atingiu o limite de fotos hoje"
        "chat_locked" -> "Converse à vontade com a Nutri"
        "weekly_report_locked" -> "Receba a análise semanal da Nutri"
        else -> "Desbloqueie o NutriMetric Premium"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            listOf(
                "15 fotos por dia (em vez de 1)",
                "Converse com a Nutri IA sobre suas refeições",
                "Relatórios semanais de evolução"
            ).forEach { benefit ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when {
                isLoadingOffering -> {
                    CircularProgressIndicator()
                }
                offering == null || offering!!.availablePackages.isEmpty() -> {
                    Text(
                        text = "Planos indisponíveis no momento. Tente novamente mais tarde.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    offering!!.availablePackages
                        .sortedByDescending { it.packageType == PackageType.ANNUAL }
                        .forEach { pkg ->
                            PackageCard(
                                pkg = pkg,
                                enabled = !isPurchasing,
                                onClick = {
                                    val activity = context as? android.app.Activity ?: return@PackageCard
                                    isPurchasing = true
                                    errorMessage = null
                                    scope.launch {
                                        val result = viewModel.purchasePackage(activity, pkg)
                                        isPurchasing = false
                                        result.onFailure { e ->
                                            errorMessage = "Não foi possível concluir a compra: ${e.message}"
                                        }
                                        if (result.isSuccess) {
                                            onBack()
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                }
            }

            Text(
                text = "Inclui período de teste grátis quando disponível na oferta da loja.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                enabled = !isPurchasing,
                onClick = {
                    scope.launch {
                        val result = viewModel.restorePurchases()
                        result.onFailure { e -> errorMessage = "Não foi possível restaurar: ${e.message}" }
                        if (result.isSuccess) onBack()
                    }
                }
            ) {
                Text("Restaurar compras")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PackageCard(pkg: Package, enabled: Boolean, onClick: () -> Unit) {
    val label = when (pkg.packageType) {
        PackageType.ANNUAL -> "Anual"
        PackageType.MONTHLY -> "Mensal"
        PackageType.WEEKLY -> "Semanal"
        else -> pkg.identifier
    }

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
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    pkg.product.price.formatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onClick, enabled = enabled) {
                Text("Assinar")
            }
        }
    }
}
