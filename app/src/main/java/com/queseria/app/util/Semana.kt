package com.queseria.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Semana de la quesería: de VIERNES a JUEVES (el jueves es el día de corte).
 * Igual que en el backend (`utils/semana.js`), cada semana se identifica con [numero]/[anio]:
 * la semana ISO de su jueves de corte.
 */
data class Semana(val inicio: LocalDate) {

    /** Jueves de corte. */
    val fin: LocalDate get() = inicio.plusDays(6)
    val numero: Int get() = fin.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val anio: Int get() = fin.get(IsoFields.WEEK_BASED_YEAR)

    fun anterior() = Semana(inicio.minusWeeks(1))
    fun siguiente() = Semana(inicio.plusWeeks(1))
    fun dias(): List<LocalDate> = (0L..6L).map { inicio.plusDays(it) }
    fun contiene(fecha: LocalDate) = !fecha.isBefore(inicio) && !fecha.isAfter(fin)
    val esActual: Boolean get() = contiene(LocalDate.now())

    /** "23 oct – 29 oct 2026" (el año del inicio solo se escribe si es distinto al del fin). */
    fun texto(): String {
        val desde = Formato.fechaLegible(inicio.toString())
        val desdeCorto = if (inicio.year == fin.year) desde.removeSuffix(" ${inicio.year}") else desde
        return "$desdeCorto – ${Formato.fechaLegible(fin.toString())}"
    }

    /** "vie 23 oct – jue 29 oct" (para el calendario por semanas). */
    fun textoConDias(): String = "${diaCorto(inicio)} – ${diaCorto(fin)}"

    companion object {
        private val DIAS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
        private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        private val DIAS_LARGOS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

        /** "vie", "sáb"… */
        fun nombreDia(fecha: LocalDate): String = DIAS[fecha.dayOfWeek.value - 1]

        /** "vie 23 oct" */
        fun diaCorto(fecha: LocalDate): String = "${nombreDia(fecha)} ${fecha.dayOfMonth} ${MESES[fecha.monthValue - 1]}"

        /** "Viernes 23 oct" */
        fun diaLargo(fecha: LocalDate): String =
            "${DIAS_LARGOS[fecha.dayOfWeek.value - 1]} ${fecha.dayOfMonth} ${MESES[fecha.monthValue - 1]}"

        /** La semana (viernes–jueves) que contiene [fecha]. */
        fun de(fecha: LocalDate): Semana = Semana(fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)))

        fun actual(): Semana = de(LocalDate.now())

        /** La semana con ese número/año (el del jueves de corte). */
        fun de(numero: Int, anio: Int): Semana {
            val jueves = LocalDate.of(anio, 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, numero.toLong())
                .with(DayOfWeek.THURSDAY)
            return Semana(jueves.minusDays(6))
        }
    }
}
