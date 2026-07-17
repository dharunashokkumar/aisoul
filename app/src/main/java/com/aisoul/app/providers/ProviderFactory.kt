package com.aisoul.app.providers

import okhttp3.OkHttpClient

class ProviderFactory(private val http: OkHttpClient) {

    fun create(type: ProviderType, apiKey: String, baseUrlOverride: String? = null): ProviderClient {
        val base = baseUrlOverride?.takeIf { it.isNotBlank() }
        return when (type) {
            ProviderType.ANTHROPIC -> AnthropicClient(http, apiKey, base ?: type.defaultBaseUrl)
            ProviderType.OPENAI -> OpenAIClient(http, apiKey, base ?: type.defaultBaseUrl)
            ProviderType.GEMINI -> GeminiClient(http, apiKey, base ?: type.defaultBaseUrl)
            ProviderType.OPENAI_COMPAT -> OpenAIClient(http, apiKey, base ?: type.defaultBaseUrl)
        }
    }
}
