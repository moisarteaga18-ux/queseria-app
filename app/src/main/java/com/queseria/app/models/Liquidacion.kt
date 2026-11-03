package com.queseria.app.models

import com.google.gson.annotations.SerializedName

// ========================================
// LIQUIDACIÓN SEMANAL AL GANADERO (semana de viernes a jueves)
// Endpoints: /api/liquidaciones, /api/descuentos
// ========================================

/** Litros recibidos un día de la semana. */
data class DiaLiquidacion(
    val fecha: String,   // "2026-10-23"
    val litros: Double = 0.0,
    val entregas: Int = 0
)

/** Descuento aplicado en una liquidación (copia del registrado en la semana). */
data class LineaDescuento(
    val descuentoId: String? = null,
    val tipo: String,
    val fecha: String? = null,
    val descripcion: String? = null,
    val litros: Double? = null,
    val valor: Double = 0.0
)

/**
 * Liquidación de un ganadero. Si [id] es null es una vista previa (todavía no se generó).
 * Mientras está pendiente el backend la recalcula; al pagarla queda congelada.
 */
data class Liquidacion(
    @SerializedName("_id") val id: String? = null,
    val numero: Int? = null,
    val ganaderoId: String,
    val nombreGanadero: String,
    val telefonoGanadero: String? = null,
    val semana: Int,
    val anio: Int,
    val fechaInicio: String,
    val fechaFin: String,
    val dias: List<DiaLiquidacion> = emptyList(),
    val litros: Double = 0.0,
    val precioLitro: Double = 0.0,
    val subtotal: Double = 0.0,
    val descuentos: List<LineaDescuento> = emptyList(),
    val totalDescuentos: Double = 0.0,
    val total: Double = 0.0,
    val estado: String = SIN_GENERAR,
    // Lo que se pagó; si después se corrigió la semana, [total] puede ser distinto
    val totalPagado: Double? = null,
    val fechaPago: String? = null,
    val formaPago: String? = null,
    val notas: String? = null,
    // Solo viene en la vista previa: false si aún no se definió el precio del litro de la semana.
    // (Gson no usa los valores por defecto de Kotlin, por eso es nullable y se lee con [tienePrecio].)
    val precioDefinido: Boolean? = null,
    val createdAt: String? = null
) {
    val pagada: Boolean get() = estado == PAGADO
    /** Lo que falta pagar (+) o se pagó de más (−) por correcciones hechas después del pago. */
    val diferenciaPago: Double get() = if (pagada && totalPagado != null) total - totalPagado else 0.0
    val generada: Boolean get() = id != null
    /** Una liquidación generada siempre tiene precio; la vista previa lo indica el backend. */
    val tienePrecio: Boolean get() = generada || precioDefinido == true

    companion object {
        const val PENDIENTE = "pendiente"
        const val PAGADO = "pagado"
        const val SIN_GENERAR = "sin_generar"
    }
}

/** Datos básicos de la liquidación dentro del resumen de la semana. */
data class ResumenLiquidacion(
    @SerializedName("_id") val id: String,
    val numero: Int,
    val estado: String,
    val total: Double = 0.0,
    val fechaPago: String? = null,
    val formaPago: String? = null
)

data class LiquidacionGanadero(
    val ganaderoId: String,
    val nombre: String,
    val telefono: String? = null,
    val litros: Double = 0.0,
    // null = falta el precio del litro de la semana
    val subtotal: Double? = null,
    val totalDescuentos: Double = 0.0,
    val total: Double? = null,
    val liquidacion: ResumenLiquidacion? = null
)

/** GET /api/liquidaciones/semana */
data class LiquidacionesSemana(
    val semana: Int,
    val anio: Int,
    val fechaInicio: String,
    val fechaFin: String,
    val texto: String? = null,
    // true cuando ya pasó el jueves de corte
    val cerrada: Boolean = false,
    val precioLitro: Double? = null,
    val totalLitros: Double = 0.0,
    val totalAPagar: Double = 0.0,
    val totalPagado: Double = 0.0,
    val totalPendiente: Double = 0.0,
    val sinLiquidar: Int = 0,
    val litrosSinGanadero: Double = 0.0,
    val ganaderos: List<LiquidacionGanadero> = emptyList()
)

/** Una semana en GET /api/liquidaciones/semanas (no genera liquidaciones). */
data class SemanaLiquidacion(
    val semana: Int,
    val anio: Int,
    val fechaInicio: String,
    val fechaFin: String,
    val texto: String? = null,
    val cerrada: Boolean = false,
    val precioLitro: Double? = null,
    val totalLitros: Double = 0.0,
    val ganaderos: Int = 0,
    val liquidaciones: Int = 0,
    val pagadas: Int = 0,
    val sinLiquidar: Int = 0,
    // Ganaderos con leche cuyo total no se puede calcular porque falta el precio del litro
    val sinPrecio: Int = 0,
    val totalAPagar: Double = 0.0,
    val totalPagado: Double = 0.0,
    val totalPendiente: Double = 0.0,
    val litrosSinGanadero: Double = 0.0
)

/** GET /api/liquidaciones/resumen */
data class ResumenPendientes(
    val pendientes: Int = 0,
    val totalPendiente: Double = 0.0,
    val ganaderos: List<String> = emptyList()
)

data class Descuento(
    @SerializedName("_id") val id: String,
    val ganaderoId: String,
    val fecha: String,
    val tipo: String,
    val valor: Double = 0.0,
    val litros: Double? = null,
    val descripcion: String? = null
)

object TipoDescuento {
    const val ADELANTO = "adelanto"
    const val PRESTAMO = "prestamo"
    const val LECHE_RECHAZADA = "leche_rechazada"
    const val OTRO = "otro"

    fun legible(tipo: String) = when (tipo) {
        ADELANTO -> "Adelanto"
        PRESTAMO -> "Préstamo"
        LECHE_RECHAZADA -> "Leche rechazada"
        else -> "Otro descuento"
    }
}

object FormaPago {
    const val EFECTIVO = "efectivo"
    const val NEQUI = "nequi"
    const val TRANSFERENCIA = "transferencia"

    fun legible(forma: String?) = when (forma) {
        EFECTIVO -> "Efectivo"
        NEQUI -> "Nequi"
        TRANSFERENCIA -> "Transferencia"
        else -> "—"
    }

    /** Para usar dentro de una frase: "efectivo", "transferencia", pero "Nequi" (es un nombre propio). */
    fun enFrase(forma: String?) = if (forma == NEQUI) legible(forma) else legible(forma).lowercase()
}

data class DescuentoRequest(
    val ganaderoId: String,
    val fecha: String,
    val tipo: String,
    val valor: Double? = null,
    val litros: Double? = null,
    val descripcion: String? = null
)

data class PrecioLecheRequest(
    val semana: Int,
    val anio: Int,
    val precioLitro: Double,
    val confirmar: Boolean? = null
)

data class GenerarLiquidacionRequest(val ganaderoId: String, val semana: Int, val anio: Int)

data class SemanaRequest(val semana: Int, val anio: Int)

data class PagarLiquidacionRequest(
    val formaPago: String,
    val fechaPago: String,
    val notas: String? = null
)

// ========================================
// MERMA DEL QUESO (cierre semanal, viernes a jueves)
// Endpoint: GET /api/merma, PUT /api/merma/{anio}/{semana}
// ========================================

/** Cierre de una semana: merma = inicial + producido − vendido − lo que pasa a la siguiente. */
data class CierreSemana(
    val semana: Int,
    val anio: Int,
    val fechaInicio: String,
    val fechaFin: String,
    val texto: String? = null,
    val stockInicialKg: Double = 0.0,
    val kgProducidos: Double = 0.0,
    val kgVendidos: Double = 0.0,
    val kgFinal: Double = 0.0,
    val confirmado: Boolean = false,
    val mermaKg: Double = 0.0,
    val mermaPct: Double = 0.0,
    val precioPorKg: Double = 0.0,
    val valorMerma: Double = 0.0
)

/** Semana en curso: stock estimado usando el % de merma promedio. */
data class SemanaMermaActual(
    val semana: Int,
    val anio: Int,
    val texto: String? = null,
    val stockInicialKg: Double = 0.0,
    val kgProducidos: Double = 0.0,
    val kgVendidos: Double = 0.0,
    val mermaEstimadaKg: Double = 0.0,
    val stockEstimadoKg: Double = 0.0
)

/** Semana recién cerrada de la que hay que confirmar cuántos kg pasan a la siguiente. */
data class CierrePendiente(
    val semana: Int,
    val anio: Int,
    val texto: String? = null,
    val sobranteEstimadoKg: Double = 0.0
)

data class MermaResumen(
    val semanas: List<CierreSemana> = emptyList(),
    val promedioPct: Double = 0.0,
    val actual: SemanaMermaActual? = null,
    val pendiente: CierrePendiente? = null
)

data class CierreRequest(val kgFinal: Double, val fecha: String)
