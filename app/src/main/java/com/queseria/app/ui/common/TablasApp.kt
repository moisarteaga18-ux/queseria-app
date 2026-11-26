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
import com.queseria.app.ui.ventas.debe
import com.queseria.app.ui.ventas.porCobrar
import com.queseria.app.ui.ventas.textoPago
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

/** "20 – 26 nov" o "30 oct – 5 nov": los días de una semana en una sola línea. */
fun rangoCorto(s: Semana): String {
    val mesFin = fechaConMes(s.fin.toString()).substringAfter(" ")
    val mesInicio = fechaConMes(s.inicio.toString()).substringAfter(" ")
    return if (mesInicio == mesFin) "${s.inicio.dayOfMonth} – ${s.fin.dayOfMonth} $mesFin" else "${s.inicio.dayOfMonth} $mesInicio – ${s.fin.dayOfMonth} $mesFin"
}

private fun plural(n: Int, uno: String, varios: String) = "$n ${if (n == 1) uno else varios}"

// ========================= VENTAS =========================

/**
 * Ventas: Fecha (fija) | Cliente | Tipo | Cantidad | Precio | Total | Pago. [porSemana] = la lista es de una sola
 * semana (la fecha va como "vie 20"); si no, "20 nov". [conCliente] = false en la página de un cliente.
 * Pago: "Pagada" o "Debe $ …" (venta fiada con saldo, en ámbar).
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
        Tabla.Columna("Total", 116, numero = true),
        Tabla.Columna("Pago", 132, numero = true)
    )
    val pago = columnas.lastIndex
    return Tabla(
        columnas = columnas,
        celdas = { v ->
            listOfNotNull(
                if (porSemana) fechaCorta(v.fecha) else fechaConMes(v.fecha),
                if (conCliente) v.cliente?.takeIf { it.isNotBlank() } ?: "Cliente ocasional" else null,
                if (v.tipoVenta == TipoVenta.MAYORISTA) "Mayor" else "Detal",
                cantidadCorta(v.cantidad, v.unidad),
                Formato.moneda(v.precioUnitario),
                Formato.moneda(v.total),
                v.textoPago
            )
        },
        marca = { v -> marcaPendiente(v.pendiente) },
        nota = { v -> v.pendiente?.etiqueta },
        alTocar = alTocar,
        colorCelda = { v, i -> if (i == pago) (if (v.porCobrar) R.color.ambar_advertencia else R.color.verde_principal) else null }
    )
}

fun totalesVentas(ventas: List<Venta>, conCliente: Boolean = true): List<String> {
    val kg = ventas.sumOf { Formato.aKg(it.cantidad, it.unidad) }
    return listOfNotNull(
        "Total",
        if (conCliente) plural(ventas.size, "venta", "ventas") else null,
        if (conCliente) "" else plural(ventas.size, "venta", "ventas"),
        Formato.kg(kg), "", Formato.moneda(ventas.sumOf { it.total }),
        ventas.sumOf { it.debe }.let { if (it > 0.5) "Debe ${Formato.moneda(it)}" else "Todo pagado" }
    )
}

// ========================= PRECIOS =========================

/** Precios: Semana (fija) | Días | Minorista kg | Mayorista kg | Litro de leche. Sin totales. */
fun tablaPrecios(alTocar: (Precio) -> Unit): Tabla<Precio> = Tabla(
    columnas = listOf(
        Tabla.Columna("Semana", 84),
        Tabla.Columna("Días", 130),
        Tabla.Columna("Minorista kg", 118, numero = true),
        Tabla.Columna("Mayorista kg", 118, numero = true),
        Tabla.Columna("Litro leche", 104, numero = true)
    ),
    celdas = { p ->
        val s = Semana.de(p.semana, p.anio)
        listOf(
            "S${p.semana}",
            rangoCorto(s),
            p.precioPorKg?.let { Formato.moneda(it) } ?: "—",
            p.precioMayoristaPorKg?.let { Formato.moneda(it) } ?: "—",
            p.precioLitro?.let { Formato.moneda(it) } ?: "—"
        )
    },
    nota = { p -> if (Semana.de(p.semana, p.anio).esActual) "actual" else if (p.anio != LocalDate.now().year) "${p.anio}" else null },
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
        Tabla.Columna("Días", 130),
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
            rangoCorto(Semana.de(s.semana, s.anio)),
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
        Tabla.Columna("Semana", 84), Tabla.Columna("Días", 130), Tabla.Columna("N°", 84)
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
            else listOf("S${l.semana}", rangoCorto(Semana.de(l.semana, l.anio)), numero)
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

/** Descuentos dentro de una liquidación: Fecha (fija) | Tipo | Valor (ya valorizado) | Detalle. El valor va antes para que se vea en la tarjeta. */
fun tablaDescuentosLiquidacion(precioLitro: Double, alTocar: ((LineaDescuento) -> Unit)?): Tabla<LineaDescuento> = Tabla(
    columnas = listOf(
        Tabla.Columna("Fecha", 76),
        Tabla.Columna("Tipo", 108),
        Tabla.Columna("Valor", 108, numero = true),
        Tabla.Columna("Detalle", 160)
    ),
    celdas = { d ->
        listOf(
            d.fecha?.let { fechaCorta(it) } ?: "—",
            TipoDescuento.legible(d.tipo),
            "−${Formato.moneda(d.valor)}",
            listOfNotNull(
                d.litros?.takeIf { d.tipo == TipoDescuento.LECHE_RECHAZADA }?.let { "${Formato.litros(it)} × ${Formato.moneda(precioLitro)}" },
                d.descripcion
            ).joinToString(" · ").ifEmpty { "—" }
        )
    },
    colorCelda = { _, i -> if (i == 2) R.color.rojo_error else null },
    alTocar = alTocar
)

// ========================= REPORTES =========================

/** Semana (viernes a jueves) de una fila de `resumen/semanas`. */
fun semanaDe(r: com.queseria.app.models.ResumenSemana): Semana =
    Semana.de(LocalDate.parse(Formato.soloFecha(r.fechaInicio)))

/** Merma de la semana como texto: "Pendiente" mientras no hay número (nunca se inventa). */
fun textoMerma(r: com.queseria.app.models.ResumenSemana): String = when {
    r.mermaKg == null -> "Pendiente"
    else -> Formato.kg(r.mermaKg)
}

fun textoValorMerma(r: com.queseria.app.models.ResumenSemana): String = when {
    r.mermaKg == null -> "—"
    else -> Formato.moneda(r.valorMerma ?: 0.0)
}

/**
 * Reportes, semana por semana: Semana (fija) | Leche | Valor leche | Queso | Ventas | Por cobrar | Merma |
 * Valor merma | Ganancia. La semana que no ha terminado su viernes siguiente dice "en curso".
 */
fun tablaReporteSemanas(alTocar: (com.queseria.app.models.ResumenSemana) -> Unit): Tabla<com.queseria.app.models.ResumenSemana> =
    Tabla(
        columnas = listOf(
            Tabla.Columna("Semana", 116),
            Tabla.Columna("Leche", 92, numero = true),
            Tabla.Columna("Valor leche", 112, numero = true),
            Tabla.Columna("Queso", 92, numero = true),
            Tabla.Columna("Ventas", 116, numero = true),
            Tabla.Columna("Por cobrar", 108, numero = true),
            Tabla.Columna("Merma", 96, numero = true),
            Tabla.Columna("Valor merma", 112, numero = true),
            Tabla.Columna("Ganancia", 120, numero = true)
        ),
        celdas = { r ->
            listOf(
                rangoCorto(semanaDe(r)),
                Formato.litros(r.litrosLeche),
                Formato.moneda(r.costoLeche),
                Formato.kg(r.kgProducidos),
                Formato.moneda(r.ingresos),
                Formato.moneda(r.porCobrar),
                textoMerma(r),
                textoValorMerma(r),
                Formato.moneda(r.ganancia)
            )
        },
        nota = { r -> "Sem. ${r.semana}" + if (!r.completa) " · en curso" else "" },
        alTocar = alTocar,
        colorCelda = { r, i ->
            when {
                i == 8 -> if (r.ganancia < 0) R.color.gr_negativo else R.color.gr_positivo
                i == 5 && r.porCobrar > 0 -> R.color.gr_oro
                i == 6 && r.mermaKg == null -> R.color.gr_texto_2
                i == 6 && r.revisar != null -> R.color.gr_negativo
                else -> null
            }
        },
        paleta = Tabla.Paleta.OSCURA
    )

fun totalesReporteSemanas(lista: List<com.queseria.app.models.ResumenSemana>): List<String> {
    val conMerma = lista.filter { it.mermaKg != null }
    return listOf(
        "Total",
        Formato.litros(lista.sumOf { it.litrosLeche }),
        Formato.moneda(lista.sumOf { it.costoLeche }),
        Formato.kg(lista.sumOf { it.kgProducidos }),
        Formato.moneda(lista.sumOf { it.ingresos }),
        Formato.moneda(lista.sumOf { it.porCobrar }),
        if (conMerma.isEmpty()) "—" else Formato.kg(conMerma.sumOf { it.mermaKg ?: 0.0 }),
        if (conMerma.isEmpty()) "—" else Formato.moneda(conMerma.sumOf { it.valorMerma ?: 0.0 }),
        Formato.moneda(lista.sumOf { it.ganancia })
    )
}

/** Una fila de "semana elegida vs. la anterior". [colorDiferencia]: recurso de color o null (neutro). */
data class FilaComparar(
    val concepto: String,
    val actual: String,
    val anterior: String,
    val diferencia: String,
    val colorDiferencia: Int? = null,
    /** Color de los valores de la semana (la ganancia: verde o rojo según el signo). */
    val colorActual: Int? = null,
    val colorAnterior: Int? = null
)

/**
 * Compara [actual] con [anterior] concepto por concepto. Diferencia con ▲/▼; en verde si es buena para el
 * negocio (más ventas, más ganancia, menos merma…), en rojo si es mala; litros y queso, sin color.
 */
fun filasComparar(actual: com.queseria.app.models.ResumenSemana, anterior: com.queseria.app.models.ResumenSemana?): List<FilaComparar> {
    // bueno: +1 = subir es bueno, −1 = subir es malo, 0 = neutro
    fun fila(nombre: String, a: Double?, b: Double?, texto: (Double) -> String, bueno: Int, siFalta: String = "—"): FilaComparar {
        val ta = a?.let(texto) ?: siFalta
        val tb = if (anterior == null) "—" else b?.let(texto) ?: siFalta
        if (a == null || b == null || anterior == null) return FilaComparar(nombre, ta, tb, "—")
        val d = a - b
        if (kotlin.math.abs(d) < 0.005) return FilaComparar(nombre, ta, tb, "=")
        val flecha = if (d > 0) "▲" else "▼"
        val color = when {
            bueno == 0 -> null
            (d > 0) == (bueno > 0) -> R.color.gr_positivo
            else -> R.color.gr_negativo
        }
        return FilaComparar(nombre, ta, tb, "$flecha ${texto(kotlin.math.abs(d))}", color)
    }
    fun signo(v: Double?) = when { v == null -> null; v < 0 -> R.color.gr_negativo; else -> R.color.gr_positivo }
    val b = anterior
    return listOf(
        fila("Leche", actual.litrosLeche, b?.litrosLeche, Formato::litros, 0),
        fila("Valor leche", actual.costoLeche, b?.costoLeche, Formato::moneda, -1),
        fila("Queso producido", actual.kgProducidos, b?.kgProducidos, Formato::kg, 0),
        fila("Ventas", actual.ingresos, b?.ingresos, Formato::moneda, 1),
        fila("Por cobrar", actual.porCobrar, b?.porCobrar, Formato::moneda, -1),
        fila("Merma", actual.mermaKg, b?.mermaKg, Formato::kg, -1, siFalta = "Pendiente"),
        fila("Valor merma", actual.mermaKg?.let { actual.valorMerma ?: 0.0 }, b?.mermaKg?.let { b.valorMerma ?: 0.0 }, Formato::moneda, -1),
        fila("Ganancia", actual.ganancia, b?.ganancia, Formato::moneda, 1)
            .copy(colorActual = signo(actual.ganancia), colorAnterior = signo(b?.ganancia))
    )
}

/** Tabla: Concepto (fija) | Semana N | Semana N−1 | Diferencia. */
fun tablaComparar(actual: Semana, anterior: Semana): Tabla<FilaComparar> =
    Tabla(
        columnas = listOf(
            Tabla.Columna("Concepto", 104),
            Tabla.Columna("Sem. ${actual.numero}", 106, numero = true),
            Tabla.Columna("Sem. ${anterior.numero}", 106, numero = true),
            Tabla.Columna("Diferencia", 112, numero = true)
        ),
        celdas = { f -> listOf(f.concepto, f.actual, f.anterior, f.diferencia) },
        colorCelda = { f, i ->
            when {
                i == 3 -> f.colorDiferencia
                i == 1 && f.colorActual != null -> f.colorActual
                i == 2 && f.colorAnterior != null -> f.colorAnterior
                (i == 1 && f.actual == "Pendiente") || (i == 2 && f.anterior == "Pendiente") -> R.color.gr_texto_2
                else -> null
            }
        },
        paleta = Tabla.Paleta.OSCURA
    )
