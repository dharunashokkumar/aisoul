package com.aisoul.app.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Shared SSE-over-OkHttp plumbing. Blocking reads run on IO; cancelling the
 * collector cancels the underlying call, which unblocks the read.
 */
internal fun sseFlow(
    client: OkHttpClient,
    buildRequest: () -> Request,
    handler: SseHandler,
): Flow<StreamEvent> = flow {
    val call = client.newCall(buildRequest())
    currentCoroutineContext().job.invokeOnCompletion { cause ->
        if (cause != null) call.cancel()
    }
    call.execute().use { response ->
        if (!response.isSuccessful) {
            throw ProviderException(extractErrorMessage(response.body?.string(), response.code))
        }
        val source = response.body?.source() ?: throw ProviderException("empty response body")
        source.forEachSseEvent { event -> handler.onEvent(this, event) }
        handler.onStreamEnd(this)
    }
}.flowOn(Dispatchers.IO)

internal interface SseHandler {
    suspend fun onEvent(collector: FlowCollector<StreamEvent>, event: SseEvent)
    suspend fun onStreamEnd(collector: FlowCollector<StreamEvent>) {}
}

/** GET a URL; success = key works. Provider error message returned verbatim. */
internal suspend fun validationGet(
    client: OkHttpClient,
    buildRequest: () -> Request,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        client.newCall(buildRequest()).execute().use { response ->
            if (!response.isSuccessful) {
                throw ProviderException(extractErrorMessage(response.body?.string(), response.code))
            }
        }
    }
}
