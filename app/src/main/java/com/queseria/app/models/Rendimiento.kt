package com.queseria.app.models

import com.google.gson.annotations.SerializedName

// ========================================
// RENDIMIENTO SEMANAL
// Endpoint: GET /api/rendimiento/semanal
// Devuelve: { semanas[], promedioLibrasPorLata, mejorSemana, peorSemana }
// ========================================

data class RendimientoSemanal(
    val semanas: List<SemanaRendimiento> = emptyList(),
    val promedioLibrasPorLata: Double = 0.0,
    val mejorSemana: SemanaRendimiento? = null,
    val peorSemana: SemanaRendimiento? = null
)

data class SemanaRendimiento(
    @SerializedName("_id") val id: SemanaId? = null,
    val totalLitros: Double = 0.0,
    val totalKg: Double = 0.0,
    val totalLatas: Double = 0.0,    // litros / 20
    val totalLibras: Double = 0.0,   // kg × 2 (libra de 500 g)
    val librasPorLata: Double = 0.0, // métrica clave: libras de queso por cada "lata" (20 litros) de leche
    val kgPorLata: Double = 0.0,
    val rango: RangoSemana? = null,
    val etiqueta: String? = null     // ejemplo: "Sem 18 · 28 abr – 4 may 2026"
)

data class SemanaId(
    val semana: Int,
    val anio: Int
)

/** Semana de viernes a jueves. */
data class RangoSemana(
    val inicio: String,   // viernes, formato: "2026-10-23"
    val fin: String,      // jueves de corte, formato: "2026-10-29"
    val texto: String     // ejemplo: "23 – 29 oct 2026"
)