package com.waldirbaia.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupFileNameEntity(
    @SerialName("file_name")
    val file_name: String
)