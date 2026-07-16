package br.com.nutrimetric.app.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Autenticação com Google (Credential Manager) + Firebase Auth.
 *
 * Funciona em dois modos:
 * - Firebase configurado (google-services.json presente): login obrigatório.
 * - Firebase ausente (ambiente de dev antes do setup): [isFirebaseConfigured]
 *   retorna false e o app segue direto para a home, sem login.
 */
class AuthRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun isLoggedIn(): Boolean = auth.currentUser != null

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        return try {
            val webClientId = resolveWebClientId(context)
                ?: return Result.failure(
                    IllegalStateException(
                        "Web Client ID não encontrado. O google-services.json foi adicionado ao módulo app?"
                    )
                )

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = CredentialManager.create(context).getCredential(context, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)

            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase não retornou o usuário após o login."))

            ensureUserDocument(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    /**
     * Cria (ou atualiza sem sobrescrever) o documento users/{uid} usado pelas
     * Cloud Functions para quota e entitlement. isPremium NUNCA é escrito pelo
     * cliente — só pelas functions/webhook (garantido pelas Firestore Rules).
     */
    private suspend fun ensureUserDocument(user: FirebaseUser) {
        val doc = mapOf(
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "updatedAt" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .set(doc, SetOptions.merge())
            .await()
    }

    /**
     * O recurso default_web_client_id é gerado pelo plugin google-services a
     * partir do google-services.json. A busca é feita em runtime para o app
     * compilar mesmo antes de o Firebase ser configurado.
     */
    private fun resolveWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        return if (resId != 0) context.getString(resId) else null
    }

    companion object {
        fun isFirebaseConfigured(context: Context): Boolean =
            FirebaseApp.getApps(context).isNotEmpty()
    }
}
