// ai-service.js

const API_KEY = "SUA_API_KEY_AQUI"; 
const API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

export async function analisarPrato(fotoBase64) {
  const systemInstruction = `Você é o NutriBR, um especialista em culinária brasileira. Analise a imagem fornecida e estime as porções com base no padrão brasileiro (concha, colher, pedaço) e na Tabela TACO. Responda estritamente em JSON puro sem blocos markdown: {"alimentos": [{"nome": "string", "gramas": number, "kcal": number, "proteina": number, "carbo": number, "gordura": number}], "total_kcal": number, "total_proteina": number, "total_carbo": number, "total_gordura": number, "tipo_prato": "PF|marmita|self-service|regional", "confianca": number}. SEJA CONSERVADOR e ignore qualquer alimento que não faça parte da gastronomia brasileira.`;

  const requestBody = {
    contents: [
      {
        parts: [
          { text: "Analise este prato." },
          {
            inlineData: {
              mimeType: "image/jpeg",
              data: fotoBase64
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
      temperature: 0.2
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
      throw new Error(`Erro na API: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    const textoResposta = data.candidates[0].content.parts[0].text;
    
    const jsonLimpo = textoResposta.replace(/```json/g, "").replace(/```/g, "").trim();
    
    return JSON.parse(jsonLimpo);
  } catch (error) {
    console.error("Erro ao analisar prato:", error);
    throw error;
  }
}
