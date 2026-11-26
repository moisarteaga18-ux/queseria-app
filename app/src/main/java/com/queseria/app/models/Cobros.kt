package com.queseria.app.models

import com.google.gson.annotations.SerializedName

// ========================================
// VENTAS FIADAS Y ABONOS (cuentas por cobrar)
// Una venta fiada se paga después con abonos; cuando los abonos llegan al total queda pagada.
// Las ventas sin "fiada" (todas las antiguas) están pagadas.
// ========================================

/** Abono (pago parcial o total) a una venta fiada. */
data class Abono(
    @SerializedName("_id") val id: String? = null,
    val ventaId: String? = null,
    val fecha: String,
    val valor: Double,
    val nota: String? = null,
    val createdAt: String? = null
)

data class AbonoRequest(
    val ventaId: String? = null,
    val fecha: String,
    val valor: Double,
    val nota: String? = null
)

/** Cómo quedó la venta después de un abono. */
data class EstadoCobro(
    @SerializedName("_id") val id: String? = null,
    val total: Double = 0.0,
    val abonado: Double = 0.0,
    val saldo: Double = 0.0,
    val pagada: Boolean = false
)

data class AbonoGuardado(val abono: Abono? = null, val venta: EstadoCobro? = null)

/** Una venta fiada que todavía debe algo. */
data class CuentaPorCobrar(
    val ventaId: String,
    val fecha: String,
    val cliente: String? = null,
    val clienteId: String? = null,
    val cantidad: Double = 0.0,
    val unidad: String = "kg",
    val tipoVenta: String = "",
    val total: Double = 0.0,
    val abonado: Double = 0.0,
    val saldo: Double = 0.0,
    val dias: Int = 0,
    val abonos: List<Abono>? = null
)

/** Lo que debe un cliente (sus ventas fiadas con saldo) y desde cuándo. */
data class DeudaCliente(
    val clienteId: String? = null,
    val cliente: String? = null,
    val debe: Double = 0.0,
    val ventas: Int = 0,
    val desde: String? = null,
    val dias: Int = 0
)

data class CuentasPorCobrar(
    val total: Double = 0.0,
    val cuentas: List<CuentaPorCobrar>? = null,
    val clientes: List<DeudaCliente>? = null
)
