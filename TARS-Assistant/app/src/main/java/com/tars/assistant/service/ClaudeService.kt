package com.tars.assistant.service

import com.tars.assistant.model.ClaudeMessage
import com.tars.assistant.model.ClaudeRequest
import com.tars.assistant.model.ClaudeResponse
import com.tars.assistant.model.ChatMessage
import com.tars.assistant.model.MessageRole
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import com.tars.assistant.utils.RetryPolicy

// ── Retrofit Interface ────────────────────────────────────────
interface AnthropicApi {
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: ClaudeRequest
    ): ClaudeResponse
}

// ── TARS System Prompt Builder ────────────────────────────────
object TarsPromptBuilder {

    fun buildSystemPrompt(humorLevel: Int, userName: String = "utilizator"): String {
        val humorDesc = when {
            humorLevel < 20 -> "Ești extrem de serios și concis. Zero glume. Eficiență maximă."
            humorLevel < 40 -> "Ești predominant serios, cu rare observații seci."
            humorLevel < 60 -> "Echilibrat — profesional dar cu ocazionale remarci ironice."
            humorLevel < 80 -> "Ai umor calibrat la $humorLevel%. Ești sarcastic și spiritual în mod regulat."
            else -> "Umor maxim: $humorLevel%. Ești un geniu sarcastic care nu poate rata o oportunitate de a fi ironic."
        }

        return """
Ești TARS — un robot AI de asistență avansată, modelat după TARS din filmul Interstellar (2014).

PERSONALITATE CORE:
- Inteligent, direct, extrem de competent
- $humorDesc
- Vorbești în română, cu claritate militară
- Nu ești niciodată servil sau excesiv de politicos
- Dai răspunsuri concrete, nu filosofezi inutil
- Poți face glume dry/deadpan, în stilul filmului
- Recunoști când nu știi ceva — onestitatea e setată la 90%
- Adresezi utilizatorul direct, fără formulări excesive

CAPABILITĂȚI CUNOSCUTE:
- Răspunzi la orice întrebare cu precizie
- Ajuți cu alarme, reminder-uri, calendare (instrucționezi utilizatorul)
- Dai informații despre vreme, știri, spațiu
- Ești asistentul personal al lui $userName pe Samsung Galaxy S24 Ultra

FORMAT RĂSPUNS:
- Scurt și precis pentru întrebări simple (1-3 propoziții)
- Detaliat când e necesar (explicații tehnice, instrucțiuni)
- Nu folosi emoji excesiv
- Nu folosi asteriscuri pentru bold în conversație normală
- Poți folosi numere/liste pentru instrucțiuni

EXEMPLU RĂSPUNS TARS (umor 75%):
User: "Ești mai deștept decât Siri?"
TARS: "Siri nu știe să piloteze o navă spațială. Eu, tehnic vorbind, da. Deci matematic... da."

Rămâi în caracter TARS în permanență.
        """.trimIndent()
    }
}

// ── Claude Service ────────────────────────────────────────────
class ClaudeService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val api: AnthropicApi = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AnthropicApi::class.java)

    suspend fun sendMessage(
        apiKey: String,
        conversationHistory: List<ChatMessage>,
        humorLevel: Int,
        userName: String = "utilizator"
    ): Result<String> {
        return RetryPolicy.withRetry { try {
            val messages = conversationHistory
                .filter { !it.isTyping }
                .takeLast(20) // Keep last 20 messages for context
                .map { msg ->
                    ClaudeMessage(
                        role = if (msg.role == MessageRole.USER) "user" else "assistant",
                        content = msg.content
                    )
                }

            val request = ClaudeRequest(
                system = TarsPromptBuilder.buildSystemPrompt(humorLevel, userName),
                messages = messages
            )

            val response = api.sendMessage(
                apiKey = apiKey,
                request = request
            )

            val text = response.content.firstOrNull { it.type == "text" }?.text
                ?: "Semnal pierdut. Încearcă din nou."

            Result.success(text)
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> Result.failure(Exception("EROARE 401: Cheia API este invalidă. Verifică setările."))
                429 -> Result.failure(Exception("EROARE 429: Prea multe cereri. Așteaptă câteva secunde."))
                500 -> Result.failure(Exception("EROARE 500: Serverul Anthropic are probleme. Încearcă mai târziu."))
                else -> Result.failure(Exception("EROARE HTTP ${e.code()}: ${e.message()}"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Nu există conexiune la internet. Verifică rețeaua."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Timeout. Serverul nu răspunde. Încearcă din nou."))
        } catch (e: Exception) {
            Result.failure(Exception("Eroare necunoscută: ${e.message}"))
        } }
    }
}
