package com.queseria.app.models

import com.google.gson.annotations.SerializedName

/** Respuesta al borrar: la copia queda en la papelera con este id (para "Deshacer"). */
data class Borrado(val papeleraId: String? = null)

/** Lo que se muestra de un registro borrado. */
data class ResumenBorrado(
    val fecha: String? = null,
    val nombre: String? = null,
    val cantidad: String? = null,
    val valor: Double? = null
)

/** GET /api/papelera: un registro borrado en los últimos 30 días. */
data class EntradaPapelera(
    @SerializedName("_id") val id: String,
    /** leche, produccion, venta, descuento o liquidacion */
    val tipo: String,
    val resumen: ResumenBorrado = ResumenBorrado(),
    val borradoEn: String,
    val expiraEn: String
) {
    val tipoLegible: String get() = when (tipo) {
        "leche" -> "Leche"
        "produccion" -> "Producción"
        "venta" -> "Venta"
        "descuento" -> "Adelanto o descuento"
        "liquidacion" -> "Liquidación anulada"
        "abono" -> "Abono"
        else -> tipo
    }
}

data class ConfirmarRequest(val confirmar: Boolean? = null)
