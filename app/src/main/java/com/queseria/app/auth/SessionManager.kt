package com.queseria.app.auth

import android.content.Context
import android.content.SharedPreferences
import com.queseria.app.api.RetrofitClient

/**
 * Maneja la sesión del usuario:
 * - Guarda y recupera el token JWT en SharedPreferences
 * - Sincroniza el token con RetrofitClient para que cada petición lo use automáticamente
 * - Permite cerrar sesión limpiando el token
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Guarda el token JWT después de un login/registro exitoso.
     * También lo sincroniza con RetrofitClient para que las próximas peticiones lo usen.
     */
    fun guardarToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        RetrofitClient.authToken = token
    }

    /**
     * Devuelve el token guardado o null si el usuario no ha iniciado sesión.
     */
    fun obtenerToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    /**
     * Guarda los datos básicos del usuario logueado (para mostrarlos en pantalla sin pedir al servidor).
     */
    fun guardarDatosUsuario(username: String, nombreNegocio: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_NOMBRE_NEGOCIO, nombreNegocio)
            .apply()
    }

    fun obtenerUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun obtenerNombreNegocio(): String? = prefs.getString(KEY_NOMBRE_NEGOCIO, null)

    /**
     * Comprueba si el usuario ya inició sesión previamente.
     * Útil al abrir la app: si hay token, se va directo a la pantalla principal.
     */
    fun haySesionActiva(): Boolean {
        return obtenerToken() != null
    }

    /**
     * Carga el token guardado en RetrofitClient.
     * Llamar al iniciar la app para que las peticiones funcionen sin necesidad de login.
     */
    fun cargarTokenEnRetrofit() {
        val token = obtenerToken()
        if (token != null) {
            RetrofitClient.authToken = token
        }
    }

    /**
     * Cierra sesión: borra el token y todos los datos del usuario.
     * Después de esto, la app debe redirigir al login.
     */
    fun cerrarSesion() {
        prefs.edit().clear().apply()
        RetrofitClient.authToken = null
    }

    companion object {
        private const val PREF_NAME = "queseria_session"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_NOMBRE_NEGOCIO = "nombre_negocio"
    }
}