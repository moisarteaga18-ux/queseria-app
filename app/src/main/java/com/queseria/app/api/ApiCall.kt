package com.queseria.app.api

import com.google.gson.Gson
import com.queseria.app.models.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Resultado de una llamada al backend.
 * Evita repetir en cada pantalla el mismo manejo de errores (red, códigos HTTP, ok=false).
 */
sealed class Resultado<out T> {
    data class Exito<T>(val data: T?) : Resultado<T>()
    data class Error(
        val mensaje: String,
        val codigo: Int? = null,
        /** El cambio afecta una liquidación ya pagada: hay que preguntar y reenviar con confirmar = true. */
        val requiereConfirmacion: Boolean = false
    ) : Resultado<Nothing>() {
        /** El token expiró o no es válido: hay que volver al login. */
        val sesionExpirada: Boolean get() = codigo == 401
    }
}

/**
 * Ejecuta una llamada de [QueseriaApi] en el hilo de IO y la convierte en [Resultado].
 *
 * Ejemplo:
 *   when (val r = llamarApi { RetrofitClient.api.getVentas() }) { ... }
 */
suspend fun <T> llamarApi(llamada: suspend () -> Response<ApiResponse<T>>): Resultado<T> {
    return try {
        val response = withContext(Dispatchers.IO) { llamada() }
        val body = response.body()
        when {
            response.isSuccessful && body?.ok == true -> Resultado.Exito(body.data)
            response.isSuccessful -> Resultado.Error(body?.error ?: body?.message ?: "Error desconocido")
            else -> errorDe(response)
        }
    } catch (e: CancellationException) {
        // La pantalla se cerró mientras esperaba: no es un error, hay que dejar que la corrutina termine
        throw e
    } catch (e: Exception) {
        Resultado.Error("Error de conexión: ${e.message ?: "sin respuesta del servidor"}")
    }
}

/**
 * Lee el { "error": "...", "requiereConfirmacion": true } que manda el backend en respuestas 4xx/5xx.
 */
private fun errorDe(response: Response<*>): Resultado.Error {
    val textoError = try {
        response.errorBody()?.string()
    } catch (e: Exception) {
        null
    }
    val cuerpo = try {
        Gson().fromJson(textoError, ApiResponse::class.java)
    } catch (e: Exception) {
        null
    }
    val mensaje = cuerpo?.let { it.error ?: it.message } ?: when (response.code()) {
        401 -> "Tu sesión expiró. Inicia sesión de nuevo."
        404 -> "Recurso no encontrado"
        else -> "Error del servidor (${response.code()})"
    }
    return Resultado.Error(mensaje, response.code(), cuerpo?.requiereConfirmacion == true)
}
