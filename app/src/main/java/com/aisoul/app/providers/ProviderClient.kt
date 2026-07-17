package com.aisoul.app.providers

import kotlinx.coroutines.flow.Flow

interface ProviderClient {
    /** Streams one model turn. Throws [ProviderException] on provider errors. */
    fun stream(request: ChatRequest): Flow<StreamEvent>

    /** Cheapest possible real call proving the key works (a models list). */
    suspend fun validateKey(): Result<Unit>
}
