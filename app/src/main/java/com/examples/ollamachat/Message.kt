package com.examples.ollamachat

data class Message(
    val role: String,        // "user" or "assistant"
    var content: String
)