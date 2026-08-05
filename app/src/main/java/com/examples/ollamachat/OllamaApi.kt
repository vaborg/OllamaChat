package com.examples.ollamachat

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object OllamaApi {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentCall: Call? = null

    fun cancelCurrent() {
        currentCall?.cancel()
    }

    /** Blocking — call from a background thread. Returns model names from /api/tags. */
    @Throws(IOException::class)
    fun listModels(host: String, apiKey: String): List<String> {
        val req = requestBuilder(host, "/api/tags", apiKey).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: throw IOException("Empty response body")
			if (!resp.isSuccessful) throw IOException(httpError(resp.code, body))
			val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
            val result = ArrayList<String>(models.length())
            for (i in 0 until models.length()) {
                val o = models.getJSONObject(i)
                val name = o.optString("name", o.optString("model"))
                if (name.isNotBlank()) result.add(name)
            }
            return result
        }
    }

    /** Streaming chat (NDJSON). Callbacks arrive on an OkHttp thread — wrap UI updates in runOnUiThread. */
    fun chatStream(
        host: String,
        apiKey: String,
        model: String,
        messages: List<Message>,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        cancelCurrent()

        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", arr)
            .put("stream", true)

        val req = requestBuilder(host, "/api/chat", apiKey)
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val call = client.newCall(req)
        currentCall = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
    response.use { r ->
        val body = r.body
        if (!r.isSuccessful) {
            val errorBody = body?.string() ?: ""
            onError(httpError(r.code, errorBody))
            return
        }

        try {
            val source = body?.source() ?: run {
                onError("Empty response body")
                return
            }

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (line.isBlank()) continue
                val json = try { JSONObject(line) } catch (e: Exception) { continue }
                if (json.has("error")) {
                    onError(json.optString("error", "Unknown server error"))
                    return
                }
                val token = json.optJSONObject("message")?.optString("content").orEmpty()
                if (token.isNotEmpty()) onToken(token)
                if (json.optBoolean("done", false)) break
            }
            onDone()
        } catch (e: Exception) {
            if (!call.isCanceled()) onError(e.message ?: "Stream error")
        }
    }
}
        })
    }

    private fun requestBuilder(host: String, path: String, apiKey: String): Request.Builder {
        val url = host.trim().removeSuffix("/") + path
        val b = Request.Builder().url(url)
        if (apiKey.isNotBlank()) b.header("Authorization", "Bearer ${apiKey.trim()}")
        return b
    }

    private fun httpError(code: Int, body: String): String {
        val serverMsg = try { JSONObject(body).optString("error", "") } catch (e: Exception) { "" }
        return when {
            code == 401 || code == 403 -> "Unauthorized (HTTP $code) — check your API key."
            serverMsg.isNotBlank() -> "HTTP $code: $serverMsg"
            else -> "HTTP $code"
        }
    }
}
