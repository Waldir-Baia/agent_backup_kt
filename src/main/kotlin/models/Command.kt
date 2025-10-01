package com.waldirbaia.models

import kotlinx.serialization.Serializable

// Garanta que os nomes das variáveis sejam IDÊNTICOS aos nomes das colunas na sua tabela Supabase.
@Serializable
data class SchedulePayload(
    val schedule_name: String,
    val rclone_command: String,
    val cron_expression: String,
    val is_active: Boolean
    // Não precisamos do client_id aqui, pois o filtro já é feito na consulta.
)