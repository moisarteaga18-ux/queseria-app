package com.queseria.app.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Estado de la conexión con el servidor, para el aviso superior ("Sin internet · datos guardados del …").
 * Lo actualiza [CacheInterceptor] en cada llamada: en línea si el servidor respondió; sin internet si no hubo
 * conexión (con la hora de los datos guardados que se mostraron, si había).
 */
object Conexion {
    data class Estado(val sinInternet: Boolean, val datosDel: Long? = null)

    private val _estado = MutableLiveData(Estado(false))
    val estado: LiveData<Estado> = _estado

    /** Se llama cuando el servidor vuelve a responder (sirve para subir lo pendiente). */
    var alVolver: (() -> Unit)? = null

    fun enLinea() {
        if (_estado.value?.sinInternet == true) {
            _estado.postValue(Estado(false))
            alVolver?.invoke()
        }
    }

    fun sinInternet(datosDel: Long?) {
        val antes = _estado.value
        // Se conserva la hora más antigua de lo que se está mostrando
        val hora = listOfNotNull(datosDel, antes?.datosDel?.takeIf { antes.sinInternet }).minOrNull()
        _estado.postValue(Estado(true, hora))
    }

    /** true si el teléfono tiene una red con internet (wifi o datos). No garantiza que el servidor responda. */
    fun hayRed(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Guarda en el teléfono la última respuesta de cada consulta (GET) y, si no hay internet, la devuelve
 * en su lugar (código 200 con el encabezado [ENCABEZADO] = hora en que se guardó). Así las pantallas
 * siguen mostrando los datos sin cambiar su código.
 *
 * La clave es la ruta con su consulta. Para las consultas "de hoy" (parámetro fecha) también se guarda
 * una copia sin la fecha: si cambió el día y no hay internet, se muestran los últimos datos (el aviso
 * dice de cuándo son). Las consultas por semana o rango (desde/hasta) solo devuelven lo de esa semana.
 */
class CacheInterceptor(private val carpeta: () -> File) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val pedido = chain.request()
        if (pedido.method != "GET") {
            return try {
                chain.proceed(pedido).also { Conexion.enLinea() }
            } catch (e: IOException) {
                Conexion.sinInternet(null)
                throw e
            }
        }
        val claves = clavesDe(pedido)
        val respuesta = try {
            chain.proceed(pedido)
        } catch (e: IOException) {
            val guardado = leer(claves) ?: run { Conexion.sinInternet(null); throw e }
            Conexion.sinInternet(guardado.second)
            return Response.Builder()
                .request(pedido)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (guardado en el teléfono)")
                .header(ENCABEZADO, guardado.second.toString())
                .body(guardado.first.toResponseBody(JSON))
                .build()
        }
        Conexion.enLinea()
        if (respuesta.code != 200) return respuesta
        val bytes = respuesta.body?.bytes() ?: return respuesta
        guardar(claves, bytes)
        return respuesta.newBuilder().body(bytes.toResponseBody(respuesta.body?.contentType() ?: JSON)).build()
    }

    /** Clave exacta y, si la consulta trae `fecha`, la clave sin ella. */
    private fun clavesDe(pedido: Request): List<String> {
        val url = pedido.url
        val exacta = url.encodedPath + "?" + (url.encodedQuery ?: "")
        if (url.queryParameter("fecha") == null) return listOf(exacta)
        val sinFecha = url.newBuilder().removeAllQueryParameters("fecha").build()
        return listOf(exacta, "sin-fecha:" + sinFecha.encodedPath + "?" + (sinFecha.encodedQuery ?: ""))
    }

    private fun archivo(clave: String) = File(carpeta(), sha1(clave) + ".json")

    private fun guardar(claves: List<String>, bytes: ByteArray) {
        try {
            carpeta().mkdirs()
            claves.forEach { clave ->
                val tmp = File(carpeta(), sha1(clave) + ".tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(archivo(clave)).also { if (!it) { archivo(clave).delete(); tmp.renameTo(archivo(clave)) } }
            }
        } catch (_: Exception) {
            // Sin espacio o sin permiso: la consulta funciona igual, solo no queda guardada
        }
    }

    private fun leer(claves: List<String>): Pair<ByteArray, Long>? {
        for (clave in claves) {
            val f = archivo(clave)
            if (f.exists()) return try { f.readBytes() to f.lastModified() } catch (_: Exception) { null }
        }
        return null
    }

    companion object {
        const val ENCABEZADO = "X-Guardado-En"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun sha1(texto: String): String =
            MessageDigest.getInstance("SHA-1").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Carpeta de las consultas guardadas (se borra al cerrar sesión: son datos de esa cuenta). */
        fun carpeta(context: Context) = File(context.filesDir, "consultas_guardadas")

        fun borrar(context: Context) {
            carpeta(context).deleteRecursively()
        }
    }
}
