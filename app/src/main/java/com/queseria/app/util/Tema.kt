package com.queseria.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Modo claro / oscuro de la app (se elige en Inicio → Configuración).
 * Se guarda en el teléfono (no en el servidor): cada teléfono puede tener el suyo.
 */
object Tema {

    const val CLARO = AppCompatDelegate.MODE_NIGHT_NO
    const val OSCURO = AppCompatDelegate.MODE_NIGHT_YES
    const val SISTEMA = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    private const val PREFS = "queseria_ajustes"
    private const val KEY_TEMA = "tema"

    /** Lo que eligió el usuario; por defecto, claro (como siempre fue la app). */
    fun actual(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TEMA, CLARO)

    /** Aplica el modo guardado (al abrir la app). */
    fun aplicar(context: Context) = AppCompatDelegate.setDefaultNightMode(actual(context))

    /** Guarda y aplica el modo: las pantallas abiertas se redibujan solas con los colores nuevos. */
    fun cambiar(context: Context, modo: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_TEMA, modo).apply()
        AppCompatDelegate.setDefaultNightMode(modo)
    }
}
