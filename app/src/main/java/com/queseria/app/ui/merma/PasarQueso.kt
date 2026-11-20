package com.queseria.app.ui.merma

import androidx.fragment.app.Fragment
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.DialogPasarQuesoBinding
import com.queseria.app.models.CierreRequest
import com.queseria.app.models.CierreSemana
import com.queseria.app.models.MermaResumen
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.toast
import com.queseria.app.util.Formato

/** Cuántas semanas cerradas hacia atrás se pueden elegir. */
private const val SEMANAS_ATRAS = 8

/**
 * "Pasar queso a la semana siguiente": opción aparte que el usuario usa solo cuando quiera (ya no hay
 * pregunta automática el viernes). Elige una semana ya cerrada (por defecto, la última) y los kg que pasan;
 * el servidor los suma a la semana siguiente y con eso calcula la merma. 0 = no pasar nada / deshacer.
 */
fun Fragment.pasarQuesoASemanaSiguiente(alGuardar: (MermaResumen?) -> Unit = {}) {
    conApi({ RetrofitClient.api.getMerma() }, alFallar = { toast("No se pudo cargar el queso de las semanas: ${it.mensaje}") }) { m ->
        // Solo semanas que ya cerraron (pasó su jueves); la primera de la lista es la semana en curso
        val cerradas = m?.semanas.orEmpty().drop(1).take(SEMANAS_ATRAS)
        if (cerradas.isEmpty()) {
            toast("Todavía no hay una semana cerrada con queso")
            return@conApi
        }
        mostrarPasarQueso(cerradas, alGuardar)
    }
}

private fun Fragment.mostrarPasarQueso(cerradas: List<CierreSemana>, alGuardar: (MermaResumen?) -> Unit) {
    val f = DialogPasarQuesoBinding.inflate(layoutInflater)
    var i = 0 // 0 = la última semana cerrada

    fun mostrar() {
        val s = cerradas[i]
        f.tvSemana.text = "Semana ${s.semana}"
        f.tvSemanaDias.text = s.texto ?: ""
        val partes = mutableListOf<String>()
        if (s.stockInicialKg > 0) partes += "Venía de la semana anterior: ${Formato.kg(s.stockInicialKg)}"
        partes += "Producido: ${Formato.kg(s.kgProducidos)}"
        partes += "Vendido: ${Formato.kg(s.kgVendidos)}" +
            if (s.kgVendidosViernesSiguiente > 0) " (${Formato.kg(s.kgVendidosViernesSiguiente)} el viernes siguiente)" else ""
        partes += "Queda por vender: ${Formato.kg(s.kgPorVender + s.kgPasados)}"
        if (s.kgPasados > 0) partes += "Ya pasaste ${Formato.kg(s.kgPasados)} a la semana siguiente."
        f.tvDetalle.text = partes.joinToString("\n")
        f.etKg.setText(if (s.kgPasados > 0) Formato.campo(s.kgPasados) else "")
        f.etKg.error = null
        f.btnSemanaAnterior.isEnabled = i < cerradas.size - 1
        f.btnSemanaSiguiente.isEnabled = i > 0
        f.btnSemanaAnterior.alpha = if (f.btnSemanaAnterior.isEnabled) 1f else 0.3f
        f.btnSemanaSiguiente.alpha = if (f.btnSemanaSiguiente.isEnabled) 1f else 0.3f
    }
    f.btnSemanaAnterior.setOnClickListener { if (i < cerradas.size - 1) { i++; mostrar() } }
    f.btnSemanaSiguiente.setOnClickListener { if (i > 0) { i--; mostrar() } }
    mostrar()

    mostrarFormulario("Pasar queso a la semana siguiente", f.root, foco = f.etKg) { dialogo ->
        // Vacío = 0 (no pasa nada)
        val kg = if (f.etKg.text.isNullOrBlank()) 0.0 else Formato.leerNumero(f.etKg.text)
        if (kg == null || kg < 0) {
            f.etKg.error = if (kg == null) "Escribe un número válido" else "Los kg no pueden ser negativos"
            return@mostrarFormulario false
        }
        val s = cerradas[i]
        val mensaje = if (kg > 0) "Listo: ${Formato.kg(kg)} de la semana ${s.semana} pasan a la siguiente"
        else "Listo: la semana ${s.semana} no pasa queso a la siguiente"
        guardarEnApi(dialogo, mensaje, {
            RetrofitClient.api.confirmarCierre(s.anio, s.semana, CierreRequest(kg, Formato.hoyIso()))
        }, alGuardar)
        false
    }
}
