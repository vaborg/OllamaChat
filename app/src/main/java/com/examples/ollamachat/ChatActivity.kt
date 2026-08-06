package com.examples.ollamachat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.examples.ollamachat.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter

    private val displayMessages = mutableListOf<Message>()   // shown in UI (includes placeholder)
    private val conversation = mutableListOf<Message>()      // sent to the API

    private var host = ""
    private var apiKey = ""
    private var selectedModel = ""
    private var generating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        host = SettingsStore.host(this)
        apiKey = SettingsStore.apiKey(this)
        if (host.isBlank()) { logout(); return }

        adapter = ChatAdapter(displayMessages)
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        binding.rvChat.layoutManager = lm
        binding.rvChat.adapter = adapter

        binding.btnSend.setOnClickListener {
            if (generating) stopGeneration() else sendMessage()
        }
        binding.btnLogout.setOnClickListener { logout() }

        loadModels()
    }

    private fun loadModels() {
        Thread {
            val result = runCatching { OllamaApi.listModels(host, apiKey) }
            runOnUiThread {
                result.onSuccess { models ->
                    if (models.isEmpty()) toast(getString(R.string.error_no_models))
                    else setupModelSpinner(models)
                }.onFailure { e ->
                    toast(getString(R.string.error_models, e.message ?: "unknown"))
                }
            }
        }.start()
    }

    private fun setupModelSpinner(models: List<String>) {
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spModels.adapter = spinnerAdapter

        val idx = models.indexOf(SettingsStore.model(this)).takeIf { it >= 0 } ?: 0
        binding.spModels.setSelection(idx)
        selectedModel = models[idx]

        binding.spModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModel = models[position]
                SettingsStore.saveModel(this@ChatActivity, selectedModel)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun sendMessage() {
        val text = binding.etInput.text.toString().trim()
        if (text.isEmpty()) return
        if (selectedModel.isBlank()) {
            toast(getString(R.string.error_no_model_selected))
            return
        }

        binding.etInput.text.clear()
        addToChat(Message("user", text), alsoInConversation = true)

        val assistantMsg = Message("assistant", "…")   // streaming placeholder
        addToChat(assistantMsg, alsoInConversation = false)

        setGenerating(true)
        var gotAnyToken = false

        OllamaApi.chatStream(
            host = host,
            apiKey = apiKey,
            model = selectedModel,
            messages = ArrayList(conversation),
            onToken = { token ->
                runOnUiThread {
                    if (!gotAnyToken) { assistantMsg.content = token; gotAnyToken = true }
                    else assistantMsg.content += token
                    notifyLast()
                }
            },
            onDone = {
                runOnUiThread {
                    if (!gotAnyToken) assistantMsg.content = "(empty response)"
                    conversation.add(Message("assistant", assistantMsg.content))
                    notifyLast()
                    setGenerating(false)
                }
            },
            onError = { error ->
                runOnUiThread {
                    if (gotAnyToken) {
                        conversation.add(Message("assistant", assistantMsg.content)) // keep clean partial
                        assistantMsg.content = assistantMsg.content + "\n\n⚠️ " + error
                    } else {
                        assistantMsg.content = "⚠️ " + error
                    }
                    notifyLast()
                    setGenerating(false)
                }
            }
        )
    }

    private fun stopGeneration() {
        OllamaApi.cancelCurrent()
        val last = displayMessages.lastOrNull()
        if (last != null && last.role == "assistant") {
            if (last.content != "…" && last.content.isNotBlank()) {
                conversation.add(Message("assistant", last.content))
            } else {
                last.content = "(stopped)"
            }
            notifyLast()
        }
        setGenerating(false)
    }

    private fun addToChat(msg: Message, alsoInConversation: Boolean) {
        displayMessages.add(msg)
        if (alsoInConversation) conversation.add(msg)
        adapter.notifyItemInserted(displayMessages.size - 1)
        binding.rvChat.scrollToPosition(displayMessages.size - 1)
    }

    private fun notifyLast() {
        adapter.notifyItemChanged(displayMessages.size - 1)
        binding.rvChat.scrollToPosition(displayMessages.size - 1)
    }

    private fun setGenerating(value: Boolean) {
        generating = value
        binding.btnSend.text = getString(if (value) R.string.stop else R.string.send)
        binding.etInput.isEnabled = !value
        binding.spModels.isEnabled = !value
    }

    private fun logout() {
        OllamaApi.cancelCurrent()
        SettingsStore.clear(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        OllamaApi.cancelCurrent()
        super.onDestroy()
    }
}