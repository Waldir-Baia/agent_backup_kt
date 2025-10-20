package com.waldirbaia.models

import kotlinx.serialization.Serializable

@Serializable
data class ExecutionPayload(
    val nome_tarefa: String,
    val comando: String
)
