package com.waldirbaia.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupLogEntity(
    @SerialName("client_id")
    val client_id: String,

    @SerialName("file_name")
    val file_name: String,

    @SerialName("file_size_bytes")
    val file_size_bytes: Long,

    @SerialName("file_creation_date")
    val file_creation_date: String,

    @SerialName("error_message")
    val error_message: String? = null
)
