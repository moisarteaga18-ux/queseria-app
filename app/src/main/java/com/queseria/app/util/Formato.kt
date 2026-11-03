package com.queseria.app.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utilidades de formato y conversión usadas en todas las pantallas.
 */
object Formato {

    /**
     * Libra colombiana de 500 g: 1 kg = 2 libras. ÚNICO lugar de la conversión en la app
     * (igual que `utils/unidades.js` en el backend). Se usa en precios, ventas, inventario,
     * rendimiento (lb por lata) y reportes.
     */
    const val LIBRAS_POR_KG = 2.0

    private val localeCo = Locale("es", "CO")

    private val formatoIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val formatoCorto = SimpleDateFormat("d MMM yyyy", localeCo)

    /** $ 12.500 */
    fun moneda(valor: Double): String {
        val nf = NumberFormat.getNumberInstance(localeCo).apply {
            maximumFractionDigits = 0
        }
        // Negativos como "−$ 12.500" (no "$ -12.500")
        return if (valor <= -0.5) "−$ ${nf.format(-valor)}" else "$ ${nf.format(maxOf(valor, 0.0))}"
    }

    /** 12,5 (máximo [decimales] decimales, sin ceros sobrantes) */
    fun numero(valor: Double, decimales: Int = 2): String {
        val nf = NumberFormat.getNumberInstance(localeCo).apply {
            maximumFractionDigits = decimales
        }
        return nf.format(valor)
    }

    fun kg(valor: Double): String = "${numero(valor)} kg"
    fun litros(valor: Double): String = "${numero(valor, 1)} L"

    fun kgALibras(kg: Double): Double = kg * LIBRAS_POR_KG
    fun librasAKg(lb: Double): Double = lb / LIBRAS_POR_KG

    /** Convierte una cantidad vendida en [unidad] ("kg", "libras" o "lb") a kilos. */
    fun aKg(cantidad: Double, unidad: String): Double =
        if (unidad.equals("lb", ignoreCase = true) || unidad.equals("libras", ignoreCase = true)) librasAKg(cantidad) else cantidad

    /** Fecha de hoy en formato ISO (yyyy-MM-dd), como la espera el backend. */
    fun hoyIso(): String = formatoIso.format(Date())

    fun aIso(fecha: Date): String = formatoIso.format(fecha)

    fun aIso(millis: Long): String = formatoIso.format(Date(millis))

    /**
     * Toma una fecha del backend ("2026-04-28" o "2026-04-28T05:00:00.000Z") y devuelve
     * solo la parte yyyy-MM-dd.
     */
    fun soloFecha(fecha: String?): String = fecha?.take(10) ?: ""

    /** "2026-04-28T..." → "28 abr 2026" */
    fun fechaLegible(fecha: String?): String {
        val iso = soloFecha(fecha)
        return try {
            formatoCorto.format(formatoIso.parse(iso)!!)
        } catch (e: Exception) {
            iso
        }
    }

    fun fechaLegible(millis: Long): String = formatoCorto.format(Date(millis))

    /** Convierte un yyyy-MM-dd a milisegundos (útil para el DatePicker). */
    fun isoAMillis(iso: String): Long? = try {
        formatoIso.parse(soloFecha(iso))?.time
    } catch (e: Exception) {
        null
    }

    /** Fecha ISO de hace [dias] días. */
    fun haceDias(dias: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -dias)
        return formatoIso.format(cal.time)
    }

    /** Lee un número que el usuario escribió, aceptando coma o punto decimal. */
    /** Número para poner en un campo de texto (sin separador de miles): 1250.0 → "1250", 12.5 → "12.5". */
    fun campo(valor: Double): String =
        java.math.BigDecimal.valueOf(Math.round(valor * 100) / 100.0).stripTrailingZeros().toPlainString()

    fun leerNumero(texto: CharSequence?): Double? =
        texto?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
}
