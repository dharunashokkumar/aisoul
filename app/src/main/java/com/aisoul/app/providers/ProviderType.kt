package com.aisoul.app.providers

/**
 * IMPLEMENTATION.md §3 — default model IDs live here and ONLY here, and are
 * user-editable free text everywhere. The app must never break because a
 * provider renamed a model.
 */
enum class ProviderType(
    val id: String,
    val display: String,
    val defaultModel: String,
    /** cheap model for the background distill pass; blank = use chat model */
    val defaultDistillModel: String,
    val defaultBaseUrl: String,
    val keyUrl: String,
) {
    ANTHROPIC(
        id = "anthropic",
        display = "anthropic",
        defaultModel = "claude-sonnet-5",
        defaultDistillModel = "claude-haiku-4-5",
        defaultBaseUrl = "https://api.anthropic.com",
        keyUrl = "https://console.anthropic.com/settings/keys",
    ),
    OPENAI(
        id = "openai",
        display = "openai",
        defaultModel = "gpt-5",
        defaultDistillModel = "gpt-5-mini",
        defaultBaseUrl = "https://api.openai.com/v1",
        keyUrl = "https://platform.openai.com/api-keys",
    ),
    GEMINI(
        id = "gemini",
        display = "google gemini",
        defaultModel = "gemini-2.5-flash",
        defaultDistillModel = "gemini-2.5-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        keyUrl = "https://aistudio.google.com/apikey",
    ),
    OPENAI_COMPAT(
        id = "compat",
        display = "openai-compatible",
        defaultModel = "",
        defaultDistillModel = "",
        defaultBaseUrl = "",
        keyUrl = "https://openrouter.ai/keys",
    );

    companion object {
        fun fromId(id: String?): ProviderType = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}
