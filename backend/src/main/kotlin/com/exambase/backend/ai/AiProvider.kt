package com.exambase.backend.ai

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AiChatMessage(val role: String, val text: String) // role: user | assistant

data class AiProviderRequest(
    val systemPrompt: String,
    val messages: List<AiChatMessage>,
    val maxOutputTokens: Int,
)

data class AiProviderResult(val text: String, val promptTokens: Int, val completionTokens: Int)

enum class AiFailure { UNAVAILABLE, RATE_LIMITED, BLOCKED, BAD_RESPONSE }

class AiProviderException(val kind: AiFailure, message: String) : RuntimeException(message)

/** Provider-neutral port. Gemini is the first adapter; another provider only needs this interface. */
interface AiProvider {
    val configured: Boolean
    suspend fun complete(request: AiProviderRequest): AiProviderResult
}

object NotConfiguredProvider : AiProvider {
    override val configured = false
    override suspend fun complete(request: AiProviderRequest): AiProviderResult =
        throw AiProviderException(AiFailure.UNAVAILABLE, "AI provider is not configured.")
}

/**
 * Gemini `generateContent` over plain HTTP (no extra dependency). The key goes in a header, never in a URL or a log line.
 * Not run against the real API yet: check the request/response shape against Google's current docs when you first enable it.
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val timeout: Duration = Duration.ofSeconds(30),
    private val maxRetries: Int = 1,
    private val retryDelay: Duration = Duration.ofMillis(350),
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : AiProvider {
    override val configured = true
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(request: AiProviderRequest): AiProviderResult {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", request.systemPrompt) }) }) })
            put("contents", buildJsonArray {
                request.messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == "assistant") "model" else "user")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", m.text) }) })
                    })
                }
            })
            put("generationConfig", buildJsonObject { put("maxOutputTokens", request.maxOutputTokens) })
        }
        val req = HttpRequest.newBuilder(URI.create("$baseUrl/models/$model:generateContent"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        var attempt = 0
        while (true) {
            val response = try {
                http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).awaitCancellable()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    attempt++
                    delay(retryDelay.toMillis())
                    continue
                }
                throw AiProviderException(AiFailure.UNAVAILABLE, "AI provider could not be reached.")
            }
            when {
                response.statusCode() == 429 -> {
                    if (attempt < maxRetries) { attempt++; delay(retryDelay.toMillis()); continue }
                    throw AiProviderException(AiFailure.RATE_LIMITED, "AI provider is busy.")
                }
                response.statusCode() >= 500 -> {
                    if (attempt < maxRetries) { attempt++; delay(retryDelay.toMillis()); continue }
                    throw AiProviderException(AiFailure.UNAVAILABLE, "AI provider is unavailable.")
                }
                response.statusCode() >= 400 -> throw AiProviderException(AiFailure.BAD_RESPONSE, "AI provider rejected the request (${response.statusCode()}).")
            }
            return parse(response.body())
        }
    }

    private suspend fun <T> java.util.concurrent.CompletableFuture<T>.awaitCancellable(): T =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel(true) }
            whenComplete { value, error ->
                if (!continuation.isActive) return@whenComplete
                if (error == null) continuation.resume(value)
                else {
                    val cause = (error as? CompletionException)?.cause ?: error
                    continuation.resumeWithException(cause)
                }
            }
        }

    internal fun parse(raw: String): AiProviderResult {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw AiProviderException(AiFailure.BAD_RESPONSE, "AI provider sent an unreadable answer.")
        val blockReason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
        if (blockReason != null) throw AiProviderException(AiFailure.BLOCKED, "The request was blocked by the provider's safety rules.")
        val cand = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw AiProviderException(AiFailure.BAD_RESPONSE, "AI provider sent no answer.")
        val text = (cand["content"]?.jsonObject?.get("parts") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }?.joinToString("") ?: ""
        if (text.isBlank()) {
            val finish = cand["finishReason"]?.jsonPrimitive?.contentOrNull
            if (finish == "SAFETY" || finish == "PROHIBITED_CONTENT") throw AiProviderException(AiFailure.BLOCKED, "The answer was blocked by the provider's safety rules.")
            throw AiProviderException(AiFailure.BAD_RESPONSE, "AI provider sent an empty answer.")
        }
        val usage = root["usageMetadata"] as? JsonObject
        val pt = (usage?.get("promptTokenCount") as? JsonPrimitive)?.intOrNull ?: 0
        val ct = (usage?.get("candidatesTokenCount") as? JsonPrimitive)?.intOrNull ?: 0
        return AiProviderResult(text.trim(), pt, ct)
    }
}
