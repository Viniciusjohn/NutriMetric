package br.com.nutrimetric.app.ui.screens

import androidx.compose.runtime.Composable

/**
 * Fina camada sobre [PlateReviewScreen] para editar uma refeição já salva,
 * reaproveitando toda a UI de revisão de itens (ajuste de porção, macros,
 * remoção) em vez de duplicá-la.
 */
@Composable
fun EditMealScreen(
    mealId: Long,
    onBack: () -> Unit
) {
    PlateReviewScreen(
        imageBase64 = "",
        imageUri = "",
        initialItems = emptyList(),
        editingMealId = mealId,
        onBack = onBack,
        onConfirmSuccess = onBack
    )
}
