package com.example.ollamachat

import android.content.Context

object SettingsStore {
    private const val PREFS_NAME = "ollama_chat_prefs"
    private const val KEY_HOST = "host"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, host: String, apiKey: String) {
        prefs(context).edit()
            .putString(KEY_HOST, host.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun host(context: Context): String = prefs(context).getString(KEY_HOST, "") ?: ""
    fun apiKey(context: Context): String = prefs(context).getString(KEY_API_KEY, "") ?: ""

    fun saveModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model).apply()
    }

    fun model(context: Context): String = prefs(context).getString(KEY_MODEL, "") ?: ""

    fun isConfigured(context: Context): Boolean = host(context).isNotBlank()

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}