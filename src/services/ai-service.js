// src/services/ai-service.js

const API_KEY = "SUA_API_KEY_AQUI"; // Lembre-se de não expor essa chave em produção num app frontend
const MODEL = "gemini-2.0-flash";
const API_URL = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${API_KEY}`;

/**
 * Envia uma imagem de um prato para a API do Gemini 2.0 Flash e retorna a análise nutricional.
 * @param {string} fotoBase64 - A imagem em formato base64 (com ou sem o prefixo data:image/...).
 * @returns {Promise<Object>} JSON parseado contendo a análise nutricional.
 */
export async function analisarPrato(fotoBase64) {
  const systemInstruction = `Você é o NutriBR, um especialista em culinária brasileira. Analise a imagem fornecida e estime as porções com base no padrão brasileiro (concha, colher, pedaço) e na Tabela TACO. Responda estritamente em JSON puro sem blocos markdown: {"alimentos": [{"nome": "string", "gramas": number, "kcal": number, "proteina": number, "carbo": number, "gordura": number}], "total_kcal": number, "total_proteina": number, "total_carbo": number, "total_gordura": number, "tipo_prato": "PF|marmita|self-service|regional", "confianca": number}. SEJA CONSERVADOR e ignore qualquer alimento que não faça parte da gastronomia brasileira.`;

  // Remove o prefixo data:image/...;base64, se existir
  const base64Limpo = fotoBase64.replace(/^data:image\/(png|jpeg|jpg|webp);base64,/, "");

  const requestBody = {
    contents: [
      {
        parts: [
          { text: "Analise este prato e forneça os macros em formato JSON estruturado." },
          {
            inlineData: {
              mimeType: "image/jpeg",
              data: base64Limpo
            }
          }
        ]
      }
    ],
    systemInstruction: {
      parts: [
        { text: systemInstruction }
      ]
    },
    generationConfig: {
      responseMimeType: "application/json",
      temperature: 0.2 // Conservador e preciso
    }
  };

  try {
    const response = await fetch(API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Erro na API (${response.status}): ${errorText}`);
    }

    const data = await response.json();
    const textoResposta = data.candidates?.[0]?.content?.parts?.[0]?.text;
    
    if (!textoResposta) {
      throw new Error("Resposta da API veio vazia ou em um formato inesperado.");
    }
    
    // Tratamento robusto para remover markdown (```json ... ```) caso a API desobedeça
    const jsonLimpo = textoResposta.replace(/```json/g, "").replace(/```/g, "").trim();
    
    return JSON.parse(jsonLimpo);
  } catch (error) {
    console.error("[NutriBR] Erro ao analisar prato:", error);
    throw error; // Repassa o erro para ser tratado pela UI
  }
}
