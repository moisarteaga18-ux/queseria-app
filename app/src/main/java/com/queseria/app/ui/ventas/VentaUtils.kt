package com.queseria.app.ui.ventas

import com.queseria.app.R
import com.queseria.app.models.Venta
import com.queseria.app.ui.common.Fila
import com.queseria.app.util.Formato

/**
 * Valores de tipoVenta que se envían al backend. "libras" es la venta minorista (el nombre se
 * conserva para no cambiar las ventas guardadas).
 */
object TipoVenta {
    const val MAYORISTA = "mayorista"
    const val LIBRAS = "libras"

    fun legible(tipo: String) = when (tipo.lowercase()) {
        MAYORISTA -> "Mayorista"
        LIBRAS -> "Minorista"
        else -> tipo.replaceFirstChar { it.uppercase() }
    }
}

/** Valores de unidad que acepta el backend. */
object Unidad {
    const val KG = "kg"
    const val LIBRAS = "libras"

    /** Abreviatura para mostrar: "kg" o "lb". */
    fun corta(unidad: String) = if (unidad.equals(LIBRAS, ignoreCase = true) || unidad == "lb") "lb" else unidad
}

/** Total de la venta: el que manda el backend o, si no viene, cantidad × precio. */
val Venta.total: Double get() = totalVenta ?: (cantidad * precioUnitario)

/** Fila de lista para una venta (se usa en Ventas y en el detalle de un cliente). */
fun filaVenta(v: Venta, mostrarCliente: Boolean = true) = Fila(
    titulo = if (mostrarCliente) (v.cliente?.takeIf { it.isNotBlank() } ?: "Cliente ocasional")
    else Formato.fechaLegible(v.fecha),
    subtitulo = "${Formato.numero(v.cantidad)} ${Unidad.corta(v.unidad)} × ${Formato.moneda(v.precioUnitario)}",
    detalle = if (mostrarCliente) "${TipoVenta.legible(v.tipoVenta)} · ${Formato.fechaLegible(v.fecha)}"
    else TipoVenta.legible(v.tipoVenta),
    valor = Formato.moneda(v.total),
    icono = if (v.tipoVenta == TipoVenta.MAYORISTA) R.drawable.ic_tienda else R.drawable.ic_ventas,
    colorIcono = if (v.tipoVenta == TipoVenta.MAYORISTA) R.color.azul_info else R.color.verde_principal,
    // Foto del queso (guía de diseño): rueda para minorista, bloques para mayorista
    imagen = if (v.tipoVenta == TipoVenta.MAYORISTA) R.drawable.img_guia_queso_cubos else R.drawable.img_guia_queso_redondo,
    valorEnPildora = true
)
