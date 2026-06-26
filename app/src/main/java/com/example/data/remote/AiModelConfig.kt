package com.example.data.remote

enum class AiModelConfig(val label: String, val modelName: String, val baseUrl: String) {
    GEMINI_25_FLASH("Gemini 2.5 Flash", "gemini-2.5-flash", "https://generativelanguage.googleapis.com/v1beta/openai/"),
    NVIDIA_NEMOTRON("NVIDIA Nemotron 12B", "nvidia/nemotron-nano-12b-v2-vl", "https://integrate.api.nvidia.com/"),
    NVIDIA_LLAMA4("NVIDIA Llama 4 17B", "meta/llama-4-maverick-17b-128e-instruct", "https://integrate.api.nvidia.com/")
}
