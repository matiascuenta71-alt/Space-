package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- DATA CLASSES FOR MOSHI SERIALIZATION ---
data class GeminiPart(val text: String? = null)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)
data class GeminiCandidate(
    val content: GeminiContent?
)

// --- RETROFIT INTERFACE ---
interface GeminiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// --- CLIENT SINGLETON ---
object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiService = retrofit.create(GeminiService::class.java)

    /**
     * Call Gemini to get recommendations based on listening history
     */
    suspend fun getSpaceDiscoveryRecommendations(
        historySummary: String,
        favoritesSummary: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getFallbackDiscovery()
        }

        val prompt = """
            You are "Space Music AI Companion".
            Based on the user's listening profile:
            - Recent history: $historySummary
            - Favorites: $favoritesSummary
            
            Provide a custom, premium Galactic Discovery Report in Spanish.
            Include:
            1. A highly creative cosmic vibe description (e.g. "Tu órbita actual está en la Nebulosa de Orión, alineada con ritmos lofi de baja frecuencia...").
            2. 3 Recommended song suggestions with cosmic titles (e.g., "Cosmic Drift - Stellar Dust", "Warp Speed - Hyperion"), artists, and a short 1-sentence cosmic description of why they fits their orbit.
            
            Keep the tone ultra-premium, futuristic, scifi, and highly engaging. Format with clean bullet points and short paragraphs. No markdown codeblocks, just clean text.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a professional sci-fi assistant for Space Music.")))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: getFallbackDiscovery()
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error fetching AI recommendation", e)
            getFallbackDiscovery()
        }
    }

    /**
     * Call Gemini to fetch or generate synchronized karaoke-style LRC lyrics for a song.
     */
    suspend fun getSynchronizedLyrics(title: String, artist: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return getFallbackLyrics(title)
        }

        val prompt = """
            Generate synchronized LRC lyrics for the song "$title" by "$artist".
            Since this is for Space Music, if you don't have the exact real lyrics, create a beautiful, custom sci-fi space themed lyric set that matches the song title!
            
            Requirements:
            - Must return standard LRC format lines, starting with bracket timestamps like [00:05] or [00:12.50].
            - Timestamps must be realistic (incrementing from [00:00] up to around [02:30]).
            - Make the lines beautiful, poetic, or matches the song title with cosmic elements (nebula, stars, rocket, lightspeed, stardust).
            - Output ONLY the LRC lyrics. Do not write introductory words or conversational greetings. Just output the lines in LRC format.
            
            Example format:
            [00:00] (Instrumental Cosmic Prelude)
            [00:10] Floating through the stardust
            [00:20] Looking at the deep black void...
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a synchronized lyrics LRC provider.")))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val lyrics = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (lyrics.isNullOrBlank()) getFallbackLyrics(title) else lyrics
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error generating lyrics", e)
            getFallbackLyrics(title)
        }
    }

    private fun getFallbackDiscovery(): String {
        return """
            🚀 **Sintonizando Frecuencias Cósmicas**
            
            Actualmente tu nave se encuentra atravesando el sector de **Nebulosas Oscuras**. Tu flujo de reproducción sugiere una fuerte afinidad por las frecuencias profundas y los sintetizadores interestelares.
            
            🌌 **Tu Órbita de Recomendaciones:**
            
            🪐 **1. Pulsar Waves — Galactic Beacon**
            *Sintetizadores pulsantes de ondas gamma ideales para mantener la concentración durante saltos hiperespaciales.*
            
            💫 **2. Solar Winds — Helios Project**
            *Melodías orgánicas y flujos ambientales que simulan el impacto del viento solar contra los escudos térmicos.*
            
            🛸 **3. Dark Matter — Singularity Suite**
            *Frecuencias de ultra-bajos y efectos envolventes que te transportarán directamente al centro de un agujero negro.*
            
            *Ajusta el ecualizador cósmico a preset 'Bass Boost' para optimizar la resonancia de estos descubrimientos.*
        """.trimIndent()
    }

    private fun getFallbackLyrics(title: String): String {
        return """
            [00:00] 🌌 Sintonizando oscilador interestelar...
            [00:08] Escuchando los susurros de $title...
            [00:15] La gravedad nos atrae hacia el centro del disco,
            [00:25] Las estrellas brillan en frecuencias de radio,
            [00:35] Viajando a la velocidad de la luz a través del vacío,
            [00:45] Sintiendo el pulso magnético del cosmos en nuestro ser...
            [00:55] (Solo de sintetizador termiónico)
            [01:15] Polvo de estrellas fluye por los conductos principales,
            [01:25] No hay arriba ni abajo en este océano galáctico,
            [01:35] Orbitando planetas olvidados por el tiempo,
            [01:45] Encontramos la resonancia perfecta en la infinidad...
            [01:55] 🚀 Fin de la transmisión interestelar.
        """.trimIndent()
    }
}
