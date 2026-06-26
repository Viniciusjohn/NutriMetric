package com.example.utils

import com.example.data.local.TacoDao
import com.example.data.local.TacoEntity
import java.text.Normalizer

object TacoMatcher {

    /**
     * Normaliza a string removendo acentos, convertendo para minúsculas
     * e removendo stop words comuns que podem atrapalhar o matching.
     */
    fun normalize(text: String): String {
        // Remove text inside parentheses
        var normalized = text.replace(Regex("\\(.*?\\)"), "")
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
        normalized = normalized.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "")
        normalized = normalized.lowercase().trim()
        
        // Remove pontuação
        normalized = normalized.replace(Regex("[^a-z0-9\\s]"), "")
        
        // Remove stop words
        val stopWords = listOf("com", "de", "ao", "a", "em", "um", "uma")
        val words = normalized.split("\\s+".toRegex())
        return words.filter { it !in stopWords }.joinToString(" ")
    }

    /**
     * Calcula a distância de Levenshtein entre duas strings.
     * Retorna um valor menor para strings mais similares.
     */
    fun levenshtein(a: String, b: String): Int {
        val lhs = normalize(a)
        val rhs = normalize(b)
        return levenshteinPreNormalized(lhs, rhs)
    }

    /**
     * Calcula a distância de Levenshtein entre duas strings pré-normalizadas.
     */
    fun levenshteinPreNormalized(lhs: String, rhs: String): Int {
        val dp = Array(lhs.length + 1) { IntArray(rhs.length + 1) }

        for (i in 0..lhs.length) {
            for (j in 0..rhs.length) {
                if (i == 0) {
                    dp[i][j] = j
                } else if (j == 0) {
                    dp[i][j] = i
                } else {
                    dp[i][j] = minOf(
                        dp[i - 1][j - 1] + if (lhs[i - 1] == rhs[j - 1]) 0 else 1,
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1
                    )
                }
            }
        }
        return dp[lhs.length][rhs.length]
    }

    /**
     * Retorna a melhor correspondência do banco TACO para o nome genérico vindo do Gemini,
     * consultando diretamente o DAO usando LIKE.
     */
    suspend fun findBestMatch(geminiName: String, candidates: List<TacoEntity>, maxDistance: Int = 10): TacoEntity? {
        val normalizedQuery = normalize(geminiName)
        if (normalizedQuery.isBlank()) return null
        
        var bestMatch: TacoEntity? = null
        var lowestDistance = Int.MAX_VALUE

        for (tacoItem in candidates) {
            val normalizedItem = normalize(tacoItem.description)
            
            // Substring match is very strong, consider it distance 0 for this heuristic
            if (normalizedItem.contains(normalizedQuery) || normalizedQuery.contains(normalizedItem)) {
                 return tacoItem
            }
            
            val distance = levenshteinPreNormalized(normalizedQuery, normalizedItem)
            if (distance < lowestDistance) {
                lowestDistance = distance
                bestMatch = tacoItem
            }
        }

        return if (lowestDistance <= maxDistance) bestMatch else null
    }

    /**
     * Extrai macros e calorias de uma string "preparo" retornada pela IA.
     * Suporta formatos como: "100g, 150kcal, carb: 20g, prot: 10g, gord: 5g",
     * "100g, 150kcal, 20C, 10P, 5G" ou variações de escrita.
     */
    fun parsePreparo(preparo: String): ParsedMacros? {
        try {
            var grams = 100
            var kcal = 0L
            var carbs = 0L
            var protein = 0L
            var fat = 0L

            // Divide a string por vírgulas ou ponto-e-vírgula para evitar interferências entre os tokens
            val parts = preparo.split(Regex("[,;]")).map { it.trim() }
            var hasParsedGrams = false

            for (part in parts) {
                if (part.isBlank()) continue

                // 1. Gramas (ex: "100g", "100 g")
                if (!hasParsedGrams) {
                    val gramsMatch = Regex("^(\\d+)\\s*g$", RegexOption.IGNORE_CASE).find(part)
                    if (gramsMatch != null) {
                        grams = gramsMatch.groupValues[1].toIntOrNull() ?: grams
                        hasParsedGrams = true
                        continue
                    }
                }

                // 2. Kcal (ex: "150kcal", "150 kcal", "150 cal", "150 calories")
                val kcalMatch = Regex("(\\d+)\\s*(?:kcal|calories|cal|k)", RegexOption.IGNORE_CASE).find(part)
                if (kcalMatch != null) {
                    kcal = kcalMatch.groupValues[1].toLongOrNull() ?: kcal
                    continue
                }

                // 3. Carboidratos
                // Sufixo (ex: "40C", "40g Carb", "40 Carb", "40 C")
                val carbsSuffixMatch = Regex("^(\\d+)\\s*(?:g\\s*)?(?:carb|carbo|carbs|c)$", RegexOption.IGNORE_CASE).find(part)
                if (carbsSuffixMatch != null) {
                    carbs = carbsSuffixMatch.groupValues[1].toLongOrNull() ?: carbs
                    continue
                }
                // Prefixo (ex: "carb: 40", "c: 40", "carb 40")
                val carbsPrefixMatch = Regex("^(?:carb|carbo|carbs|c):?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(part)
                if (carbsPrefixMatch != null) {
                    carbs = carbsPrefixMatch.groupValues[1].toLongOrNull() ?: carbs
                    continue
                }

                // 4. Proteínas
                // Sufixo (ex: "20P", "20g Prot", "20 Prot", "20 P")
                val proteinSuffixMatch = Regex("^(\\d+)\\s*(?:g\\s*)?(?:prot|proteina|p)$", RegexOption.IGNORE_CASE).find(part)
                if (proteinSuffixMatch != null) {
                    protein = proteinSuffixMatch.groupValues[1].toLongOrNull() ?: protein
                    continue
                }
                // Prefixo (ex: "prot: 20", "p: 20", "prot 20")
                val proteinPrefixMatch = Regex("^(?:prot|proteina|p):?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(part)
                if (proteinPrefixMatch != null) {
                    protein = proteinPrefixMatch.groupValues[1].toLongOrNull() ?: protein
                    continue
                }

                // 5. Gorduras
                // Sufixo (ex: "12G", "12g Gord", "12 Gord", "12 G", "12g Fat", "12 Fat")
                val fatSuffixMatch = Regex("^(\\d+)\\s*(?:g\\s*)?(?:gord|gordura|g|fat|f)$", RegexOption.IGNORE_CASE).find(part)
                if (fatSuffixMatch != null) {
                    fat = fatSuffixMatch.groupValues[1].toLongOrNull() ?: fat
                    continue
                }
                // Prefixo (ex: "gord: 12", "g: 12", "fat: 12", "gord 12")
                val fatPrefixMatch = Regex("^(?:gord|gordura|g|fat|f):?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(part)
                if (fatPrefixMatch != null) {
                    fat = fatPrefixMatch.groupValues[1].toLongOrNull() ?: fat
                    continue
                }
            }

            if (kcal > 0 || carbs > 0 || protein > 0 || fat > 0) {
                return ParsedMacros(grams, kcal, protein, carbs, fat)
            }
        } catch (e: Exception) {
            android.util.Log.e("TacoMatcher", "Erro ao parsear preparo: $preparo", e)
        }
        return null
    }
}

data class ParsedMacros(
    val grams: Int,
    val kcal: Long,
    val protein: Long,
    val carbs: Long,
    val fat: Long
)

