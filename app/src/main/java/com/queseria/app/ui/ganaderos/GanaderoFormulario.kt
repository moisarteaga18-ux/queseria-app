package com.queseria.app.ui.ganaderos

import androidx.fragment.app.Fragment
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.DialogGanaderoBinding
import com.queseria.app.models.Ganadero
import com.queseria.app.models.GanaderoRequest
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario

/**
 * Formulario para crear ([ganadero] = null) o editar un ganadero.
 * Se usa desde la lista de ganaderos y al registrar leche.
 */
fun Fragment.abrirFormularioGanadero(ganadero: Ganadero?, alGuardar: (Ganadero?) -> Unit = {}) {
    val f = DialogGanaderoBinding.inflate(layoutInflater)
    ganadero?.let {
        f.etNombre.setText(it.nombre)
        f.etTelefono.setText(it.telefono)
    }
    mostrarFormulario(if (ganadero == null) "Nuevo ganadero" else "Editar ganadero", f.root) { dialogo ->
        val nombre = f.etNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            f.etNombre.error = "Escribe el nombre"
            return@mostrarFormulario false
        }
        val request = GanaderoRequest(nombre, f.etTelefono.text.toString().trim().ifEmpty { null })
        if (ganadero == null) {
            guardarEnApi(dialogo, "Ganadero agregado", { RetrofitClient.api.crearGanadero(request) }, alGuardar)
        } else {
            guardarEnApi(dialogo, "Ganadero actualizado", { RetrofitClient.api.actualizarGanadero(ganadero.id, request) }, alGuardar)
        }
        false
    }
}
