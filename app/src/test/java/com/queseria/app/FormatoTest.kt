package com.queseria.app

import com.queseria.app.ui.home.LecheFragment
import com.queseria.app.util.Formato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoTest {

    @Test
    fun convierteLibrasAKilos() {
        assertEquals(1.0, Formato.aKg(2.0, "lb"), 1e-9)
        assertEquals(3.5, Formato.aKg(3.5, "kg"), 1e-9)
        assertEquals(1.0, Formato.aKg(2.0, "LB"), 1e-9)
        // El backend usa "libras"
        assertEquals(1.0, Formato.aKg(2.0, "libras"), 1e-9)
    }

    @Test
    fun calculaLibrasPorLata() {
        // Rendimiento = (kg × 2) ÷ (litros ÷ 20): 100 L = 5 latas; 10 kg = 20 lb → 4 lb por lata
        assertEquals(4.0, LecheFragment.librasPorLata(100.0, 10.0), 1e-9)
        // Ejemplo real: 63 L → 9,5 kg = 19 lb en 3,15 latas
        assertEquals(6.031746, LecheFragment.librasPorLata(63.0, 9.5), 1e-6)
        assertEquals(0.0, LecheFragment.librasPorLata(0.0, 10.0), 1e-9)
    }

    @Test
    fun leeNumerosConComaOPunto() {
        assertEquals(12.5, Formato.leerNumero("12,5")!!, 1e-9)
        assertEquals(12.5, Formato.leerNumero(" 12.5 ")!!, 1e-9)
        assertNull(Formato.leerNumero("abc"))
        assertNull(Formato.leerNumero(""))
    }

    @Test
    fun noLeeNumerosInfinitos() {
        assertNull(Formato.leerNumero("1e400"))
        assertNull(Formato.leerNumero("Infinity"))
        assertNull(Formato.leerNumero("NaN"))
    }

    @Test
    fun validaNumerosMayoresQueCero() {
        // Correcto: devuelve el valor y ningún error
        assertEquals(12.5 to null, Formato.validarPositivo("12,5", "Escribe los litros", "Los litros", plural = true))
        // Vacío: el mensaje de "falta"
        assertEquals(null to "Escribe los litros", Formato.validarPositivo("  ", "Escribe los litros", "Los litros", plural = true))
        // No es número
        assertEquals(null to "Escribe un número válido", Formato.validarPositivo("abc", "Ingresa el precio", "El precio"))
        // Cero o negativo (defecto D-17), en plural y en singular
        assertEquals(null to "Los litros deben ser mayores que 0", Formato.validarPositivo("0", "x", "Los litros", plural = true))
        assertEquals(null to "Los kilos deben ser mayores que 0", Formato.validarPositivo("-5", "x", "Los kilos", plural = true))
        assertEquals(null to "El precio debe ser mayor que 0", Formato.validarPositivo("-9000", "x", "El precio"))
    }

    @Test
    fun extraeFechaDeTextoIso() {
        assertEquals("2026-04-28", Formato.soloFecha("2026-04-28T05:00:00.000Z"))
        assertEquals("2026-04-28", Formato.soloFecha("2026-04-28"))
        assertEquals("", Formato.soloFecha(null))
    }

    @Test
    fun formateaMonedaConNegativos() {
        assertEquals("$ 12.500", Formato.moneda(12500.0))
        assertEquals("−$ 593.800", Formato.moneda(-593800.0))
        assertEquals("$ 0", Formato.moneda(-0.2))
    }
}
