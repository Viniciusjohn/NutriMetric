package br.com.nutrimetric.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.nutrimetric.app.repository.AuthRepository
import br.com.nutrimetric.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    // Ao logar, hidrata o perfil da nuvem (quem já fez onboarding em outro
    // aparelho não refaz) antes de navegar.
    fun onAuthenticated() {
        scope.launch {
            viewModel.hydrateProfileFromCloud()
            onLoginSuccess()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NutriMetric",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Sua nutricionista de bolso",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        val result = authRepository.signInWithGoogle(context)
                        isLoading = false
                        result.fold(
                            onSuccess = { onAuthenticated() },
                            onFailure = { e ->
                                errorMessage = when {
                                    e.message?.contains("cancel", ignoreCase = true) == true ->
                                        "Login cancelado."
                                    else -> "Não foi possível entrar: ${e.message}"
                                }
                            }
                        )
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_google_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Entrar com Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  ou com e-mail  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    errorMessage = null
                    if (password.length < 6) {
                        errorMessage = "A senha precisa ter pelo menos 6 caracteres."
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val result = if (isSignUp) {
                            authRepository.signUpWithEmail(email, password)
                        } else {
                            authRepository.signInWithEmail(email, password)
                        }
                        isLoading = false
                        result.fold(
                            onSuccess = { onAuthenticated() },
                            onFailure = { e -> errorMessage = mapAuthError(e, isSignUp) }
                        )
                    }
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (isSignUp) "Criar conta" else "Entrar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null }, enabled = !isLoading) {
                Text(
                    if (isSignUp) "Já tenho conta — entrar" else "Não tenho conta — criar uma"
                )
            }

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Ao entrar, você concorda que o NutriMetric não substitui o acompanhamento de um nutricionista.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Traduz os erros comuns do Firebase Auth (e-mail/senha) para PT-BR. */
private fun mapAuthError(e: Throwable, isSignUp: Boolean): String {
    val msg = e.message ?: ""
    return when {
        msg.contains("email address is badly formatted", true) ||
            msg.contains("badly formatted", true) -> "E-mail inválido."
        msg.contains("password is invalid", true) ||
            msg.contains("INVALID_LOGIN_CREDENTIALS", true) ||
            msg.contains("supplied auth credential", true) -> "E-mail ou senha incorretos."
        msg.contains("no user record", true) ||
            msg.contains("There is no user", true) -> "Não achei uma conta com esse e-mail."
        msg.contains("email address is already in use", true) ||
            msg.contains("EMAIL_EXISTS", true) -> "Esse e-mail já tem conta. Tente entrar."
        msg.contains("network", true) -> "Sem conexão. Verifique sua internet."
        msg.contains("Password should be at least", true) -> "A senha precisa ter pelo menos 6 caracteres."
        else -> if (isSignUp) "Não foi possível criar a conta: $msg"
                else "Não foi possível entrar: $msg"
    }
}
