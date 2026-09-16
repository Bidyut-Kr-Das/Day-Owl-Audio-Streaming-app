package com.example.dayowl.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val hostName: String,
    val ipAddress: String,
    val port: Int,
    val sessionName: String
)
