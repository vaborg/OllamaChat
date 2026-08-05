package com.example.ollamachat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ollamachat.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SettingsStore.isConfigured(this)) {   // skip login next time
            openChat()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener { connect() }
    }

    private fun connect() {
        val host = binding.etHost.text?.toString()?.trim().orEmpty()
        val apiKey = binding.etApiKey.text?.toString()?.trim().orEmpty()

        if (host.isBlank()) {
            binding.etHost.error = getString(R.string.error_host_required)
            return
        }

        setLoading(true)
        Thread {
            val result = runCatching { OllamaApi.listModels(host, apiKey) }
            runOnUiThread {
                setLoading(false)
                result.onSuccess {
                    SettingsStore.save(this, host, apiKey)
                    openChat()
                }.onFailure { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.error_connect, e.message ?: "unknown"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnConnect.isEnabled = !loading
    }

    private fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java))
        finish()
    }
}