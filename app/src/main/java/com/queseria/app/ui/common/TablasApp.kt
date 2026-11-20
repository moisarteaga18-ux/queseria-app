package com.queseria.app.ui.common

import com.queseria.app.R
import com.queseria.app.models.Descuento
import com.queseria.app.models.FormaPago
import com.queseria.app.models.LineaDescuento
import com.queseria.app.models.Liquidacion
import com.queseria.app.models.LiquidacionGanadero
import com.queseria.app.models.Precio
import com.queseria.app.models.SemanaLiquidacion
import com.queseria.app.models.TipoDescuento
import com.queseria.app.models.Venta
import com.queseria.app.offline.etiqueta
import com.queseria.app.ui.liquidaciones.LiquidacionPdf
import com.queseria.app.ui.ventas.TipoVenta
import com.queseria.app.ui.ventas.total
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate

/*
 * Tablas tipo hoja de cálculo de toda la app (mismo estilo que Leche): aquí se definen las columnas de
 * cada lista y su fila de totales, para que todas se vean y se comporten igual.
 */

/** "20 nov" (con el año si no es el actual: "20 nov 25"). Para listas de varias semanas. */
fun fechaConMes(fecha: String?): String {
    val f = runCatching { LocalDate.parse(Formato.soloFecha(fecha)) }.getOrNull() ?: return ""
    val mes = Semana.diaCorto(f).substringAfterLast(' ')
    return "${f.dayOfMonth} $mes" + if (f.year != LocalDate.now().year) " ${f.year % 100}" else ""
}

private fun plural(n: Int, uno: String, varios: String) = "$n ${if (n == 1) uno else varios}"

// ========================= VENTAS =========================

/**
 * Ventas: Fecha (fija) | Cliente | Tipo | Cantidad | Precio | Total. [porSemana] = la lista es de una sola
 * semana (la fecha va como "vie 20"); si no, "20 nov". [conCliente] = false en la página de un cliente.
 */
fun tablaVentas(
    porSemana: Boolean,
    conCliente: Boolean = true,
    alTocar: ((Venta) -> Unit)? = null
): Tabla<Venta> {
    val columnas = listOfNotNull(
        Tabla.Columna("Fecha", 84),
        if (conCliente) Tabla.Columna("Cliente", 140) else null,
        Tabla.Columna("Tipo", 74),
        Tabla.Columna("Cantidad", 96, numero = true),
        Tabla.Columna("Precio", 100, numero = true),
        Tabla.Columna("Total", 116, numero = true)
    )
    return Tabla(
        columnas = columnas,
        celdas = { v ->
            listOfNotNull(
                if (porSemana) fechaCorta(v.fecha) else fechaConMes(v.fecha),
                if (conCliente) v.cliente?.takeIf { it.isNotBlank() } ?: "Cliente ocasional" else null,
                if (v.tipoVenta == TipoVenta.MAYORISTA) "Mayor" else "Detal",
                cantidadCorta(v.cantidad, v.unidad),
                Formato.moneda(v.precioUnitario),
                Formato.moneda(v.total)
            )
        },
        marca = { v -> marcaPendiente(v.pendiente) },
        nota = { v -> v.pendiente?.etiqueta },
        alTocar = alTocar
    )
}

fun totalesVentas(ventas: List<Venta>, conCliente: Boolean = true): List<String> {
    val kg = ventas.sumOf { Formato.aKg(it.cantidad, it.unidad) }
    return listOfNotNull(
        "Total",
        if (conCliente) plural(ventas.size, "venta", "ventas") else null,
        if (conCliente) "" else plural(ventas.size, "venta", "ventas"),
        Formato.kg(kg), "", Formato.moneda(ventas.sumOf { it.total })
    )
}

// ========================= PRECIOS =========================

/** Precios: Semana (fija) | Días | Minorista kg | Mayorista kg | Litro de leche. Sin totales. */
fun tablaPrecios(alTocar: (Precio) -> Unit): Tabla<Precio> = Tabla(
    columnas = listOf(
        Tabla.Columna("Semana", 84),
        Tabla.Columna("Días", 150),
        Tabla.Columna("Minorista kg", 118, numero = true),
        Tabla.Columna("Mayorista kg", 118, numero = true),
        Tabla.Columna("Litro leche", 104, numero = true)
    ),
    celdas = { p ->
        val s = Semana.de(p.semana, p.anio)
        listOf(
            "S${p.semana}",
            s.textoConDias(),
            p.precioPorKg?.let { Formato.moneda(it) } ?: "—",
            p.precioMayoristaPorKg?.let { Formato.moneda(it) } ?: "—",
            p.precioLitro?.let { Formato.moneda(it) } ?: "—"
        )
    },
    nota = { p -> if (Semana.de(p.semana, p.anio).esActual) "esta" else if (p.anio != LocalDate.now().year) "${p.anio}" else null },
    marca = { p -> if (p.precioPorKg == null || p.precioLitro == null) Tabla.Marca.AVISO else Tabla.Marca.NINGUNA },
    alTocar = alTocar
)

// ========================= LIQUIDACIONES =========================

private fun estadoSemana(s: SemanaLiquidacion): String = when {
    s.ganaderos == 0 -> "Sin leche"
    s.sinPrecio > 0 -> "Falta precio"
    s.pagadas == s.ganaderos -> "Pagada"
    !s.cerrada -> "En curso"
    else -> "Por pagar"
}

/** Liquidaciones por semana: Semana (fija) | Días | Ganaderos | Litros | Total | Por pagar | Estado. */
fun tablaSemanasLiquidacion(alTocar: (SemanaLiquidacion) -> Unit): Tabla<SemanaLiquidacion> = Tabla(
    columnas = listOf(
        Tabla.Columna("Semana", 84),
        Tabla.Columna("Días", 150),
        Tabla.Columna("Ganaderos", 98, numero = true),
        Tabla.Columna("Litros", 90, numero = true),
        Tabla.Columna("Total", 116, numero = true),
        Tabla.Columna("Por pagar", 116, numero = true),
        Tabla.Columna("Estado", 108)
    ),
    celdas = { s ->
        val sinTotal = s.ganaderos == 0 || s.sinPrecio > 0
        listOf(
            "S${s.semana}",
            Semana.de(s.semana, s.anio).textoConDias(),
            s.ganaderos.toString(),
            Formato.numero(s.totalLitros, 1),
            if (sinTotal) "—" else Formato.moneda(s.totalAPagar),
            if (sinTotal) "—" else Formato.moneda(s.totalPendiente),
            estadoSemana(s)
        )
    },
    nota = { s -> if (!s.cerrada) "en curso" else null },
    marca = { s -> if (s.sinPrecio > 0) Tabla.Marca.AVISO else Tabla.Marca.NINGUNA },
    colorCelda = { s, i ->
        if (i != 6) null else when (estadoSemana(s)) {
            "Pagada" -> R.color.verde_exito
            "Por pagar", "Falta precio" -> R.color.ambar_advertencia
            "En curso" -> R.color.azul_info
            else -> R.color.gris_texto_secundario
        }
    },
    alTocar = alTocar
)

fun totalesSemanasLiquidacion(lista: List<SemanaLiquidacion>): List<String> {
    val conTotal = lista.filter { it.ganaderos > 0 && it.sinPrecio == 0 }
    return listOf(
        "Total", plural(lista.size, "semana", "semanas"), "", Formato.numero(lista.sumOf { it.totalLitros }, 1),
        Formato.moneda(conTotal.sumOf { it.totalAPagar }), Formato.moneda(conTotal.sumOf { it.totalPendiente }), ""
    )
}

private fun textoPago(l: Liquidacion) =
    if (l.pagada) "${fechaConMes(l.fechaPago)} · ${FormaPago.legible(l.formaPago)}" else "—"

/**
 * Historial de liquidaciones. [conGanadero]: N° (fija) | Ganadero | Semana | Litros | Total | Estado | Pago.
 * Sin ganadero (historial de uno solo): Semana (fija) | Días | N° | Litros | Total | Estado | Pago.
 */
fun tablaLiquidaciones(conGanadero: Boolean, alTocar: (Liquidacion) -> Unit): Tabla<Liquidacion> {
    val columnas = if (conGanadero) listOf(
        Tabla.Columna("N°", 84), Tabla.Columna("Ganadero", 130), Tabla.Columna("Semana", 80)
    ) else listOf(
        Tabla.Columna("Semana", 84), Tabla.Columna("Días", 150), Tabla.Columna("N°", 84)
    )
    return Tabla(
        columnas = columnas + listOf(
            Tabla.Columna("Litros", 90, numero = true),
            Tabla.Columna("Total", 116, numero = true),
            Tabla.Columna("Estado", 98),
            Tabla.Columna("Pago", 150)
        ),
        celdas = { l ->
            val numero = LiquidacionPdf.numeroTexto(l.numero).removePrefix("N° ")
            val inicio = if (conGanadero) listOf(numero, l.nombreGanadero, "S${l.semana}")
            else listOf("S${l.semana}", Semana.de(l.semana, l.anio).textoConDias(), numero)
            inicio + listOf(
                Formato.numero(l.litros, 1),
                Formato.moneda(l.total),
                if (l.pagada) "Pagado" else "Pendiente",
                textoPago(l)
            )
        },
        nota = { l -> if (!conGanadero && l.anio != LocalDate.now().year) "${l.anio}" else null },
        colorCelda = { l, i -> if (i == 5) (if (l.pagada) R.color.verde_exito else R.color.ambar_advertencia) else null },
        alTocar = alTocar
    )
}

fun totalesLiquidaciones(lista: List<Liquidacion>): List<String> {
    val pendiente = lista.filterNot { it.pagada }.sumOf { it.total }
    return listOf(
        "Total", plural(lista.size, "liquidación", "liquidaciones"), "",
        Formato.numero(lista.sumOf { it.litros }, 1), Formato.moneda(lista.sumOf { it.total }),
        if (pendiente > 0) "Debe ${Formato.moneda(pendiente)}" else "Todo pagado", ""
    )
}

/** Ganaderos de una semana: Ganadero (fija) | Litros | Subtotal | Descuentos | Total | N° | Estado. */
fun tablaGanaderosSemana(alTocar: (LiquidacionGanadero) -> Unit): Tabla<LiquidacionGanadero> {
    fun estado(g: LiquidacionGanadero) = when {
        g.liquidacion == null -> "Sin generar"
        g.liquidacion.estado == Liquidacion.PAGADO -> "Pagado"
        else -> "Pendiente"
    }
    return Tabla(
        columnas = listOf(
            Tabla.Columna("Ganadero", 110),
            Tabla.Columna("Litros", 84, numero = true),
            Tabla.Columna("Subtotal", 112, numero = true),
            Tabla.Columna("Descuentos", 112, numero = true),
            Tabla.Columna("Total", 116, numero = true),
            Tabla.Columna("N°", 72),
            Tabla.Columna("Estado", 104)
        ),
        celdas = { g ->
            listOf(
                g.nombre,
                Formato.numero(g.litros, 1),
                g.subtotal?.let { Formato.moneda(it) } ?: "—",
                if (g.totalDescuentos > 0) "−${Formato.moneda(g.totalDescuentos)}" else "—",
                g.total?.let { Formato.moneda(it) } ?: "Sin precio",
                g.liquidacion?.numero?.let { "%04d".format(it) } ?: "—",
                estado(g)
            )
        },
        colorCelda = { g, i ->
            when (i) {
                3 -> if (g.totalDescuentos > 0) R.color.rojo_error else null
                6 -> when (estado(g)) {
                    "Pagado" -> R.color.verde_exito
                    "Pendiente" -> R.color.ambar_advertencia
                    else -> R.color.gris_texto_secundario
                }
                else -> null
            }
        },
        alTocar = alTocar
    )
}

fun totalesGanaderosSemana(lista: List<LiquidacionGanadero>): List<String> {
    val sinPrecio = lista.any { it.total == null }
    val descuentos = lista.sumOf { it.totalDescuentos }
    return listOf(
        "Total", Formato.numero(lista.sumOf { it.litros }, 1),
        if (sinPrecio) "—" else Formato.moneda(lista.sumOf { it.subtotal ?: 0.0 }),
        if (descuentos > 0) "−${Formato.moneda(descuentos)}" else "—",
        if (sinPrecio) "—" else Formato.moneda(lista.sumOf { it.total ?: 0.0 }), "", ""
    )
}

// ========================= ADELANTOS Y DESCUENTOS =========================

/** Valor de un descuento: dinero, o litros si es leche rechazada (se valoriza en la liquidación). */
fun valorDescuento(tipo: String, valor: Double, litros: Double?): String =
    if (tipo == TipoDescuento.LECHE_RECHAZADA && valor <= 0) "${Formato.numero(litros ?: 0.0, 1)} L"
    else "−${Formato.moneda(valor)}"

/** Adelantos y descuentos (todos): Fecha (fija) | Ganadero | Tipo | Detalle | Valor. */
fun tablaDescuentos(nombreGanadero: (String) -> String, alTocar: (Descuento) -> Unit): Tabla<Descuento> = Tabla(
    columnas = listOf(
        Tabla.Columna("Fecha", 84),
        Tabla.Columna("Ganadero", 124),
        Tabla.Columna("Tipo", 118),
        Tabla.Columna("Detalle", 150),
        Tabla.Columna("Valor", 112, numero = true)
    ),
    celdas = { d ->
        listOf(
            fechaCorta(d.fecha),
            nombreGanadero(d.ganaderoId),
            TipoDescuento.legible(d.tipo),
            d.descripcion ?: "—",
            valorDescuento(d.tipo, d.valor, d.litros)
        )
    },
    colorCelda = { _, i -> if (i == 4) R.color.rojo_error else null },
    alTocar = alTocar
)

fun totalesDescuentos(lista: List<Descuento>): List<String> {
    val litros = lista.filter { it.tipo == TipoDescuento.LECHE_RECHAZADA }.sumOf { it.litros ?: 0.0 }
    return listOf(
        "Total", plural(lista.size, "registro", "registros"),
        if (litros > 0) "${Formato.numero(litros, 1)} L rechazados" else "", "",
        "−${Formato.moneda(lista.sumOf { it.valor })}"
    )
}

/** Descuentos dentro de una liquidación: Fecha (fija) | Tipo | Detalle | Valor (ya valorizado). */
fun tablaDescuentosLiquidacion(precioLitro: Double, alTocar: ((LineaDescuento) -> Unit)?): Tabla<LineaDescuento> = Tabla(
    columnas = listOf(
        Tabla.Columna("Fecha", 84),
        Tabla.Columna("Tipo", 118),
        Tabla.Columna("Detalle", 160),
        Tabla.Columna("Valor", 112, numero = true)
    ),
    celdas = { d ->
        listOf(
            d.fecha?.let { fechaCorta(it) } ?: "—",
            TipoDescuento.legible(d.tipo),
            listOfNotNull(
                d.litros?.takeIf { d.tipo == TipoDescuento.LECHE_RECHAZADA }?.let { "${Formato.litros(it)} × ${Formato.moneda(precioLitro)}" },
                d.descripcion
            ).joinToString(" · ").ifEmpty { "—" },
            "−${Formato.moneda(d.valor)}"
        )
    },
    colorCelda = { _, i -> if (i == 3) R.color.rojo_error else null },
    alTocar = alTocar
)
