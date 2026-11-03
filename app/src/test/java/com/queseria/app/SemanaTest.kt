package com.queseria.app

import com.queseria.app.util.Semana
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Mismos casos que se verificaron en el backend (utils/semana.js). */
class SemanaTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun laSemanaVaDeViernesAJueves() {
        val s = Semana.de(d("2026-10-23")) // viernes
        assertEquals(d("2026-10-23"), s.inicio)
        assertEquals(d("2026-10-29"), s.fin)
        assertEquals(44, s.numero)
        assertEquals(2026, s.anio)
    }

    @Test
    fun elJuevesEsElDiaDeCorte() {
        assertEquals(43, Semana.de(d("2026-10-22")).numero) // jueves: cierra la semana 43
        assertEquals(44, Semana.de(d("2026-10-29")).numero) // jueves siguiente: cierra la 44
        assertEquals(45, Semana.de(d("2026-10-30")).numero) // viernes: empieza la 45
    }

    @Test
    fun cambioDeAnio() {
        assertEquals(53, Semana.de(d("2026-12-31")).numero)
        assertEquals(2026, Semana.de(d("2026-12-31")).anio)
        val primera = Semana.de(d("2027-01-01"))
        assertEquals(1, primera.numero)
        assertEquals(2027, primera.anio)
        assertEquals(d("2027-01-07"), primera.fin)
    }

    @Test
    fun seReconstruyeDesdeNumeroYAnio() {
        assertEquals(d("2026-10-23"), Semana.de(44, 2026).inicio)
        assertEquals(d("2027-01-01"), Semana.de(1, 2027).inicio)
        assertEquals(d("2026-12-25"), Semana.de(53, 2026).inicio)
        assertEquals(Semana.de(53, 2026), Semana.de(1, 2027).anterior())
    }

    @Test
    fun textosDelCalendarioPorSemanas() {
        val semana = Semana.de(44, 2026)
        assertEquals("vie 23 oct – jue 29 oct", semana.textoConDias())
        assertEquals(listOf("vie", "sáb", "dom", "lun", "mar", "mié", "jue"), semana.dias().map { Semana.nombreDia(it) })
        assertEquals("Domingo 25 oct", Semana.diaLargo(d("2026-10-25")))
    }
}
