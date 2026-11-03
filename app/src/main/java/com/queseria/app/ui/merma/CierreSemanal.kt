package com.queseria.app.ui.merma

import androidx.fragment.app.Fragment
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.DialogCierreBinding
import com.queseria.app.models.CierrePendiente
import com.queseria.app.models.CierreRequest
import com.queseria.app.models.MermaResumen
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.util.Formato

/**
 * Al cierre de la semana (pasado el jueves), solo si parece que sobró queso, pide un único dato:
 * cuántos kg pasan a la semana siguiente. Con eso el backend calcula la merma.
 */
fun Fragment.preguntarCierre(pendiente: CierrePendiente, promedioPct: Double, alGuardar: (MermaResumen?) -> Unit) {
    val f = DialogCierreBinding.inflate(layoutInflater)
    f.tvExplicacion.text = "La semana ${pendiente.semana} (${pendiente.texto ?: ""}) cerró el jueves.\n" +
        "Según lo producido y lo vendido" +
        (if (promedioPct > 0) " (y la merma promedio de ${Formato.numero(promedioPct, 1)} %)" else "") +
        " pudieron quedar unos ${Formato.numero(pendiente.sobranteEstimadoKg, 1)} kg. ¿Cuánto queso tienes para la semana siguiente?"

    mostrarFormulario("Cierre de la semana", f.root) { dialogo ->
        // Vacío = no sobró nada
        val kg = if (f.etKg.text.isNullOrBlank()) 0.0 else Formato.leerNumero(f.etKg.text)
        if (kg == null || kg < 0) {
            f.etKg.error = "Ingresa los kg (0 si se vendió todo)"
            return@mostrarFormulario false
        }
        guardarEnApi(dialogo, "Cierre guardado: pasan ${Formato.kg(kg)}", {
            RetrofitClient.api.confirmarCierre(pendiente.anio, pendiente.semana, CierreRequest(kg, Formato.hoyIso()))
        }, alGuardar)
        false
    }
}
