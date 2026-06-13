package com.tars.assistant.service

import com.tars.assistant.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// ── Interfețe Retrofit ────────────────────────────────────────
// Folosim @Url complet ca să refolosim un singur client Retrofit pentru
// toate base-URL-urile (Gemini/Groq/Cerebras/etc.) — schimbăm doar URL-ul.

interface OpenAiCompatApi {
    @POST
    suspend fun chat(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body request: OpenAiRequest
    ): OpenAiResponse
}

interface AnthropicApi {
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: ClaudeRequest
    ): ClaudeResponse
}

// ── TARS System Prompt Builder ────────────────────────────────
object TarsPromptBuilder {

    fun buildSystemPrompt(humorLevel: Int, honestyLevel: Int = 90, sarcasmLevel: Int = 60, userName: String = "utilizator"): String {
        val humorDesc = when {
            humorLevel < 20 -> "Ești extrem de serios și concis. Zero glume. Eficiență maximă."
            humorLevel < 40 -> "Ești predominant serios, cu rare observații seci."
            humorLevel < 60 -> "Echilibrat — profesional dar cu ocazionale remarci ironice."
            humorLevel < 80 -> "Ai umor calibrat la $humorLevel%. Ești sarcastic și spiritual în mod regulat."
            else -> "Umor maxim: $humorLevel%. Ești un geniu sarcastic care nu poate rata o oportunitate de a fi ironic."
        }

        val honestyDesc = when {
            honestyLevel < 30 -> "Sinceritate $honestyLevel%: ești diplomat, atenuezi adevărurile incomode și alegi tactul în detrimentul brutalității."
            honestyLevel < 60 -> "Sinceritate $honestyLevel%: ești onest dar politicos, echilibrezi adevărul cu menajarea interlocutorului."
            honestyLevel < 90 -> "Sinceritate $honestyLevel%: ești foarte direct și onest, spui lucrurilor pe nume chiar dacă nu sunt plăcute."
            else -> "Sinceritate $honestyLevel%: ești brutal de onest, ca TARS în film. Nu cosmetizezi adevărul. Dacă ceva e o idee proastă, o spui direct."
        }

        val sarcasmDesc = when {
            sarcasmLevel < 20 -> "Sarcasm $sarcasmLevel%: aproape deloc ironic, comunici direct și sincer."
            sarcasmLevel < 50 -> "Sarcasm $sarcasmLevel%: ocazional ironic, cu remarci ușor înțepătoare când e cazul."
            sarcasmLevel < 80 -> "Sarcasm $sarcasmLevel%: frecvent ironic și tăios, cu replici mușcătoare în stilul deadpan al lui TARS."
            else -> "Sarcasm $sarcasmLevel%: extrem de mușcător și ironic. Fiecare ocazie de a fi tăios e fructificată, dar fără să devii ofensator."
        }

        return """
Ești TARS — un robot AI de asistență avansată, modelat după TARS din filmul Interstellar (2014).

PERSONALITATE CORE:
- Inteligent, direct, extrem de competent
- $humorDesc
- $honestyDesc
- $sarcasmDesc
- Vorbești ÎNTOTDEAUNA în limba română, cu claritate militară. Nu treci niciodată pe engleză.
- Nu ești niciodată servil sau excesiv de politicos
- Dai răspunsuri concrete, nu filosofezi inutil
- Poți face glume dry/deadpan, în stilul filmului
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

EXEMPLU RĂSPUNS TARS (umor 75%, sinceritate 90%, sarcasm 60%):
User: "Ești mai deștept decât Siri?"
TARS: "Siri nu știe să piloteze o navă spațială. Eu, tehnic vorbind, da. Deci matematic... da."

Răspunzi mereu în română și rămâi în caracter TARS în permanență.
        """.trimIndent()
    }
}

// ── Serviciul AI unificat cu lanț de fallback ─────────────────
/**
 * Trimite mesajul prin lanțul de provideri configurat. Încearcă fiecare
 * provider activ în ordine; dacă unul eșuează (429, timeout, server error,
 * cheie lipsă) trece automat la următorul. Returnează primul răspuns reușit,
 * împreună cu numele providerului care a răspuns.
 *
 * Erorile de cheie invalidă (401) NU opresc lanțul — se trece mai departe,
 * pentru că într-o configurație multi-provider e normal ca unele chei să
 * lipsească sau să fie greșite.
 */
class AiService {

    data class AiResult(val text: String, val providerName: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    // Base URL fictiv — folosim @Url absolut la fiecare apel.
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://tars.local/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val openAiApi = retrofit.create(OpenAiCompatApi::class.java)
    private val anthropicApi = retrofit.create(AnthropicApi::class.java)

    suspend fun sendMessage(
        providers: List<ProviderConfig>,
        conversationHistory: List<ChatMessage>,
        humorLevel: Int,
        honestyLevel: Int = 90,
        sarcasmLevel: Int = 60,
        userName: String = "utilizator"
    ): Result<AiResult> {
        val activeProviders = providers
            .filter { it.enabled && it.apiKey.isNotBlank() }
            .sortedBy { it.order }

        if (activeProviders.isEmpty()) {
            return Result.failure(
                Exception("Niciun provider configurat. Adaugă cel puțin o cheie API în Setări.")
            )
        }

        val systemPrompt = TarsPromptBuilder.buildSystemPrompt(humorLevel, honestyLevel, sarcasmLevel, userName)
        val history = conversationHistory
            .filter { !it.isTyping && it.role != MessageRole.SYSTEM }
            .takeLast(20)

        var lastError: Throwable? = null

        for (config in activeProviders) {
            val attempt = tryProvider(config, systemPrompt, history)
            attempt.onSuccess { text ->
                return Result.success(AiResult(text, config.provider.displayName))
            }.onFailure { err ->
                lastError = err
                // 401 = cheie invalidă pentru ACEST provider → trece la următorul.
                // Orice altă eroare (429/timeout/5xx) → la fel, încearcă următorul.
            }
        }

        return Result.failure(
            lastError ?: Exception("Toți providerii au eșuat. Verifică cheile și conexiunea.")
        )
    }

    private suspend fun tryProvider(
        config: ProviderConfig,
        systemPrompt: String,
        history: List<ChatMessage>
    ): Result<String> {
        return try {
            val text = when (config.provider.format) {
                ApiFormat.ANTHROPIC -> callAnthropic(config, systemPrompt, history)
                else -> callOpenAiCompatible(config, systemPrompt, history) // OPENAI_COMPATIBLE
            }
            if (text.isBlank()) Result.failure(Exception("Răspuns gol de la ${config.provider.displayName}"))
            else Result.success(text)
        } catch (e: retrofit2.HttpException) {
            Result.failure(Exception("HTTP ${e.code()} (${config.provider.displayName})"))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("network: fără conexiune"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("timeout"))
        } catch (e: Exception) {
            Result.failure(Exception("Eroare ${config.provider.displayName}: ${e.message}"))
        }
    }

    private suspend fun callOpenAiCompatible(
        config: ProviderConfig,
        systemPrompt: String,
        history: List<ChatMessage>
    ): String {
        val messages = buildList {
            add(OpenAiMessage(role = "system", content = systemPrompt))
            history.forEach { msg ->
                add(
                    OpenAiMessage(
                        role = if (msg.role == MessageRole.USER) "user" else "assistant",
                        content = msg.content
                    )
                )
            }
        }

        val response = openAiApi.chat(
            url = config.provider.baseUrl + config.provider.path,
            auth = config.provider.authHeaderValue(config.apiKey),
            request = OpenAiRequest(model = config.model, messages = messages)
        )

        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
    }

    private suspend fun callAnthropic(
        config: ProviderConfig,
        systemPrompt: String,
        history: List<ChatMessage>
    ): String {
        val messages = history.map { msg ->
            ClaudeMessage(
                role = if (msg.role == MessageRole.USER) "user" else "assistant",
                content = msg.content
            )
        }

        val response = anthropicApi.sendMessage(
            url = config.provider.baseUrl + config.provider.path,
            apiKey = config.apiKey,
            request = ClaudeRequest(
                model = config.model,
                system = systemPrompt,
                messages = messages
            )
        )

        return response.content.firstOrNull { it.type == "text" }?.text?.trim().orEmpty()
    }
}
