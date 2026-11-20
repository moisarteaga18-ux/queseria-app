package com.queseria.app.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.auth.SessionManager
import com.queseria.app.models.LecheRequest
import com.queseria.app.models.ProduccionRequest
import com.queseria.app.models.VentaRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Sube los registros hechos sin internet ([Pendientes]) en el orden en que se hicieron.
 * Se ejecuta al volver la red, al abrir la app y cuando el servidor vuelve a responder. Nunca hay dos
 * subidas a la vez, y como cada registro lleva su idLocal, un reenvío no se duplica en el servidor.
 * - Guardado (201) o ya estaba (200 "repetido") → se quita de la cola.
 * - El servidor lo rechaza (400, 404, 409…) → queda como rechazado con el mensaje: el usuario decide.
 * - Sin conexión, servidor caído (5xx), sesión vencida o demasiados intentos → se para y se intenta después.
 */
object Sincronizador {
    data class Resumen(val subidos: Int, val rechazados: List<Pendiente>)

    private val gson = Gson()
    private val mutex = Mutex()
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _resumen = MutableLiveData<Resumen?>()
    /** Resultado de la última subida con novedades (lo muestra MainActivity y luego lo consume). */
    val resumen: LiveData<Resumen?> = _resumen
    fun consumir() = _resumen.postValue(null)

    private var registrado = false

    /** Escucha la red del teléfono: cuando vuelve, comprueba el servidor y sube lo pendiente. */
    fun iniciar(context: Context) {
        val app = context.applicationContext
        Pendientes.refrescar(app)
        Conexion.alVolver = { subir(app) }
        if (registrado) return
        registrado = true
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    alcance.launch {
                        // Si el servidor responde, el aviso "Sin internet" se quita y se sube lo pendiente
                        if (RetrofitClient.probar()) Conexion.enLinea()
                        subirAhora(app)
                    }
                }
                override fun onLost(network: Network) {
                    if (!Conexion.hayRed(app)) Conexion.sinInternet(null)
                }
            })
        } catch (_: Exception) {
            // Sin permiso o límite de callbacks: se sube al abrir la app o al volver el servidor
        }
    }

    fun subir(context: Context) {
        alcance.launch { subirAhora(context.applicationContext) }
    }

    /** Reintenta un registro rechazado (si [confirmado], el usuario aceptó cambiar una semana pagada). */
    fun reintentar(context: Context, p: Pendiente, confirmado: Boolean = false) {
        Pendientes.actualizar(context, p.copy(estado = Pendiente.PENDIENTE, error = null, confirmado = p.confirmado || confirmado))
        subir(context)
    }

    suspend fun subirAhora(context: Context): Resumen? {
        if (!mutex.tryLock()) return null
        try {
            if (RetrofitClient.authToken == null) return null
            val usuario = SessionManager(context).obtenerUsername()
            var subidos = 0
            val rechazados = mutableListOf<Pendiente>()
            for (p in Pendientes.de(context, usuario).filter { !it.rechazado }.sortedBy { it.creado }) {
                when (val r = enviar(p)) {
                    is Resultado.Exito -> { Pendientes.subido(context, p.idLocal); subidos++ }
                    is Resultado.Error -> {
                        val transitorio = r.sinConexion || r.codigo == null || r.codigo >= 500 || r.codigo == 401 || r.codigo == 429
                        if (transitorio) break
                        val rechazado = p.copy(estado = Pendiente.RECHAZADO, error = r.mensaje, requiereConfirmacion = r.requiereConfirmacion)
                        Pendientes.actualizar(context, rechazado)
                        rechazados += rechazado
                    }
                }
            }
            val resumen = Resumen(subidos, rechazados)
            if (subidos > 0 || rechazados.isNotEmpty()) _resumen.postValue(resumen)
            return resumen
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun enviar(p: Pendiente): Resultado<*> {
        val confirmar = p.confirmado.takeIf { it }
        return when (p.tipo) {
            Pendiente.LECHE -> {
                val req = gson.fromJson(p.cuerpo, LecheRequest::class.java).copy(idLocal = p.idLocal, confirmar = confirmar)
                llamarApi { RetrofitClient.api.crearLeche(req) }
            }
            Pendiente.PRODUCCION -> {
                val req = gson.fromJson(p.cuerpo, ProduccionRequest::class.java).copy(idLocal = p.idLocal)
                llamarApi { RetrofitClient.api.crearProduccion(req) }
            }
            Pendiente.VENTA -> {
                val req = gson.fromJson(p.cuerpo, VentaRequest::class.java).copy(idLocal = p.idLocal)
                llamarApi { RetrofitClient.api.crearVenta(req) }
            }
            else -> Resultado.Error("Tipo de registro desconocido: ${p.tipo}", 400)
        }
    }
}
