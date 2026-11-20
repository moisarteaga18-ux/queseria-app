package com.queseria.app

import com.google.gson.Gson
import com.queseria.app.models.LecheRequest
import com.queseria.app.models.Venta
import com.queseria.app.models.VentaRequest
import com.queseria.app.offline.Pendiente
import com.queseria.app.offline.comoLeche
import com.queseria.app.offline.comoVenta
import com.queseria.app.offline.etiqueta
import com.queseria.app.offline.textoHora
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Registros hechos sin internet (offline/): lo que se guarda en el teléfono y cómo se muestra. */
class SinInternetTest {
    private val gson = Gson()

    @Test
    fun elPedidoGuardadoLlevaSuIdLocal() {
        val req = VentaRequest(tipoVenta = "mayorista", clienteId = "c1", cantidad = 3.0, unidad = "kg", precioUnitario = 16000.0,
            fecha = "2026-11-19", idLocal = "abc-123-def")
        val json = gson.toJson(req)
        assertTrue(json.contains("\"idLocal\":\"abc-123-def\""))
        assertEquals("abc-123-def", gson.fromJson(json, VentaRequest::class.java).idLocal)
    }

    @Test
    fun unaVentaPendienteSeMuestraConSuTotalYMarca() {
        val req = VentaRequest(tipoVenta = "libras", clienteId = "c1", cantidad = 4.0, unidad = "libras", precioUnitario = 9000.0,
            fecha = "2026-11-19", idLocal = "v-0001-xyz")
        val p = Pendiente("v-0001-xyz", Pendiente.VENTA, gson.toJson(req), "2026-11-19", "venta de $ 36.000 a Rosa", "Rosa", "usuario_prueba")
        val v = p.comoVenta()
        assertEquals(36000.0, v.totalVenta!!, 0.001)
        assertEquals("Rosa", v.cliente)
        assertEquals("Pendiente", v.pendiente?.etiqueta)
        assertNull(v.id) // no tiene id del servidor: no se puede corregir ni borrar hasta que suba
    }

    @Test
    fun rechazadoDiceNoSeSubio() {
        val p = Pendiente("l-0001-xyz", Pendiente.LECHE, gson.toJson(LecheRequest(litros = 120.0, ganaderoId = "g1", fecha = "2026-11-19")),
            "2026-11-19", "120 L de Juan", "Juan", "usuario_prueba", estado = Pendiente.RECHAZADO, error = "Ganadero no encontrado")
        assertTrue(p.rechazado)
        assertEquals("No se subió", p.etiqueta)
        assertEquals("Juan", p.comoLeche().nombreGanadero)
    }

    @Test
    fun laMarcaDePendienteNoViajaEnElJson() {
        // pendiente es @Transient: ni se envía al servidor ni se lee de sus respuestas
        val p = Pendiente("x-0001-xyz", Pendiente.VENTA, "{}", "2026-11-19", "d", null, null)
        val v = Venta(fecha = "2026-11-19", tipoVenta = "mayorista", cantidad = 1.0, unidad = "kg", precioUnitario = 1.0, pendiente = p)
        assertFalse(gson.toJson(v).contains("pendiente"))
        assertNull(gson.fromJson("""{"fecha":"2026-11-19","tipoVenta":"mayorista","cantidad":1,"unidad":"kg","precioUnitario":1}""", Venta::class.java).pendiente)
    }

    @Test
    fun horaDelAviso() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 19, 21, 40, 0) }
        assertEquals("19 nov, 9:40 p. m.", textoHora(c.timeInMillis))
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 5)
        assertEquals("19 nov, 12:05 a. m.", textoHora(c.timeInMillis))
    }
}
