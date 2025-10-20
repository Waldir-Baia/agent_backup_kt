package com.waldirbaia.models

import kotlinx.serialization.Serializable

@Serializable
data class SchedulePayload(
    val schedule_name: String,
    val rclone_command: String,
    val cron_expression: String,
    val is_active: Boolean
)