package com.example.ollamachat

data class Message(
    val role: String,        // "user" or "assistant"
    var content: String
)