package br.com.nutrimetric.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.com.nutrimetric.app.data.local.ChatMessageEntity
import br.com.nutrimetric.app.ui.viewmodel.ChatViewModel
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    mainViewModel: MainViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    val messages by chatViewModel.messages.collectAsState()
    val isPremium by chatViewModel.isPremium.collectAsState()
    val isSending by chatViewModel.isSending.collectAsState()
    val error by chatViewModel.error.collectAsState()
    val premiumRequired by chatViewModel.premiumRequired.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Resultado de navegação: PlateReviewScreen grava o id da refeição salva
    // no savedStateHandle antes de voltar (ver MainActivity.kt, rota "analysis").
    val currentBackStackEntry = navController.currentBackStackEntry
    val savedMealIdState = currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<Long?>("justSavedMealId", null)
        ?.collectAsState()

    LaunchedEffect(savedMealIdState?.value) {
        val id = savedMealIdState?.value
        if (id != null) {
            chatViewModel.onMealLogged(id)
            currentBackStackEntry?.savedStateHandle?.remove<Long>("justSavedMealId")
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    LaunchedEffect(premiumRequired) {
        if (premiumRequired) {
            chatViewModel.clearPremiumRequired()
            onNavigateToPaywall()
        }
    }

    LaunchedEffect(error) {
        error?.let { message ->
            snackbarHostState.showSnackbar(message)
            chatViewModel.clearError()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                mainViewModel.setCurrentImageUri(uri)
                onNavigateToAnalysis()
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Nutri", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                if (isPremium) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onNavigateToCamera) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Tirar foto")
                        }
                        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Escolher da galeria")
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Pergunte algo para a Nutri...") },
                            modifier = Modifier.weight(1f),
                            enabled = !isSending,
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotEmpty()) {
                                    chatViewModel.sendMessage(text)
                                    inputText = ""
                                }
                            },
                            enabled = !isSending && inputText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPaywall() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Converse à vontade com a Nutri", fontWeight = FontWeight.Bold)
                            Text(
                                "Assine o Premium para desbloquear perguntas livres",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Text(
                    text = "A Nutri é uma IA e não substitui nutricionista ou médico.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 12.dp)
                )
            }

            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Oi! Sou a Nutri 👋 Registre uma refeição pra eu comentar, ou me pergunte algo (Premium).",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message -> MessageBubble(message) }
                    if (isSending) {
                        item { TypingIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageEntity) {
    when (message.role) {
        "system" -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        else -> {
            val fromUser = message.role == "user"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nutri está digitando...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
