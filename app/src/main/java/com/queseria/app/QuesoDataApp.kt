package com.queseria.app

import android.app.Application
import com.queseria.app.util.Tema

/** Arranque de la app: aplica el modo claro/oscuro guardado antes de abrir cualquier pantalla. */
class QuesoDataApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Tema.aplicar(this)
    }
}
