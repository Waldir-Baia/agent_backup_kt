package com.waldirbaia.models

import kotlinx.serialization.Serializable

@Serializable
data class ClientInfo(
    val client_id: String,
    val nome_empresa: String,
    val cnpj_empresa: String
)