package com.jsf.app.medication_solution.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val role: String, val content: String)

object OllamaService {
    // 10.0.2.2 = host machine from Android emulator; change to PC IP for real device
    private var baseUrl = "http://10.0.2.2:11434"
    private var model = "llama3.2"

    fun configure(url: String, modelName: String) {
        baseUrl = url.trimEnd('/')
        model = modelName
    }

    fun saveUrl(context: android.content.Context, url: String) {
        context.getSharedPreferences("ollama_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("base_url", url.trimEnd('/')).apply()
        baseUrl = url.trimEnd('/')
    }

    fun loadUrl(context: android.content.Context) {
        val saved = context.getSharedPreferences("ollama_prefs", android.content.Context.MODE_PRIVATE)
            .getString("base_url", "http://10.0.2.2:11434") ?: "http://10.0.2.2:11434"
        baseUrl = saved
    }

    fun currentUrl(): String = baseUrl

    suspend fun chat(messages: List<ChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$baseUrl/api/chat")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            val body = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("messages", JSONArray().apply {
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                // Low temperature for more predictable medical answers
                put("options", JSONObject().apply {
                    put("temperature", 0.7)
                    put("num_predict", 200)
                })
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(response)
                json.getJSONObject("message").getString("content").trim()
            } else {
                val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                throw Exception("Ollama error ${conn.responseCode}: $err")
            }
        }
    }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$baseUrl/api/tags")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val arr = JSONObject(resp).getJSONArray("models")
                (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
            } else emptyList()
        }.getOrDefault(emptyList())
    }
}
