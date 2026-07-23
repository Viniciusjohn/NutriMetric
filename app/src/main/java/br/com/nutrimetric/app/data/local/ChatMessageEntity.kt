package br.com.nutrimetric.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Uma mensagem do chat com a Nutri IA. Histórico só existe localmente (Room). */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Preenchido em mensagens de sistema geradas após salvar uma refeição. */
    val relatedMealId: Long? = null
)
