package com.queseria.app

import android.app.Application
import com.queseria.app.api.RetrofitClient
import com.queseria.app.offline.Sincronizador
import com.queseria.app.util.Tema

/**
 * Arranque de la app: aplica el modo claro/oscuro guardado antes de abrir cualquier pantalla, prepara las
 * consultas guardadas (para ver los datos sin internet) y la subida de lo registrado sin internet.
 */
class QuesoDataApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Tema.aplicar(this)
        RetrofitClient.iniciar(this)
        Sincronizador.iniciar(this)
    }
}
