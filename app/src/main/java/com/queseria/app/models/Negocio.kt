package com.queseria.app.models

import com.google.gson.annotations.SerializedName

// ========================================
// PRODUCTO (tipo de queso)
// El stock se guarda siempre en kilos; en pantalla se muestra también en libras.
// ========================================
data class Producto(
    @SerializedName("_id") val id: String,
    val nombre: String,
    val descripcion: String? = null,
    val precioPorKg: Double = 0.0,
    val stockKg: Double = 0.0,
    val stockMinimoKg: Double = 0.0
) {
    val stockBajo: Boolean get() = stockMinimoKg > 0 && stockKg <= stockMinimoKg
}

data class ProductoRequest(
    val nombre: String,
    val descripcion: String? = null,
    val precioPorKg: Double = 0.0,
    val stockMinimoKg: Double = 0.0,
    // Solo al crear: entra al inventario como "Stock inicial"
    val stockInicialKg: Double? = null
)

// ========================================
// MOVIMIENTO DE INVENTARIO
// cantidadKg es positiva para entradas y negativa para salidas.
// ========================================
data class MovimientoInventario(
    @SerializedName("_id") val id: String,
    val productoId: String,
    val nombreProducto: String = "",
    val tipo: String,
    val cantidadKg: Double,
    val motivo: String? = null,
    val referencia: String? = null,
    val fecha: String
) {
    companion object {
        const val ENTRADA = "ENTRADA"
        const val SALIDA = "SALIDA"
        const val AJUSTE = "AJUSTE"
    }
}

/** En ENTRADA/SALIDA [cantidadKg] es la cantidad; en AJUSTE es el stock real contado. */
data class MovimientoRequest(
    val productoId: String,
    val tipo: String,
    val cantidadKg: Double,
    val motivo: String? = null
)

// ========================================
// CLIENTE
// ========================================
data class Cliente(
    @SerializedName("_id") val id: String = "",
    val nombre: String,
    val telefono: String? = null,
    val direccion: String? = null,
    val tipo: String = MINORISTA,
    val notas: String? = null
) {
    companion object {
        const val MAYORISTA = "Mayorista"
        const val MINORISTA = "Minorista"
    }
}

// ========================================
// GANADERO (le vende la leche a la quesería)
// ========================================
data class Ganadero(
    @SerializedName("_id") val id: String = "",
    val nombre: String,
    val telefono: String? = null,
    val activo: Boolean = true
)

data class GanaderoRequest(
    val nombre: String,
    val telefono: String? = null
)

data class EliminarGanaderoResponse(val archivado: Boolean = false)

