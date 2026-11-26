package com.queseria.app.ui.merma

import android.view.View
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.DialogRegistrarMermaBinding
import com.queseria.app.models.CierreSemana
import com.queseria.app.models.MermaRequest
import com.queseria.app.ui.common.Cambio
import com.queseria.app.ui.common.Dato
import com.queseria.app.ui.common.avisarNoGuardado
import com.queseria.app.ui.common.confirmarBorrado
import com.queseria.app.ui.common.confirmarCambios
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.ofrecerDeshacerCon
import com.queseria.app.ui.common.toast
import com.queseria.app.util.Formato
import kotlinx.coroutines.launch
import kotlin.math.max

/** Cuántas semanas cerradas hacia atrás se pueden elegir. */
private const val SEMANAS_ATRAS = 8

/** Kg que caben como merma: lo que sobró menos lo que se pasó a la semana siguiente. */
private val CierreSemana.maximoMerma: Double get() = max(0.0, (sobranteKg ?: 0.0) - kgPasados)

/** Precio con el que se valora la merma: promedio real de venta (o el registrado si no hubo ventas). */
private val CierreSemana.precioValor: Double? get() = precioPromedioKg ?: precioMermaKg

/**
 * "Registrar merma" (manual, 25 nov 2026): la app ya no calcula la merma sola. Se elige una semana que ya
 * cerró (desde su viernes siguiente); se muestra lo que sobró y se propone como merma (menos lo que se pasó
 * a la semana siguiente). El usuario puede cambiar el número. [semana]/[anio]: abrir esa semana (desde
 * Reportes o Ganancias); sin ellas, la primera pendiente o la última cerrada.
 */
fun Fragment.registrarMerma(semana: Int? = null, anio: Int? = null, alGuardar: () -> Unit = {}) {
    conApi({ RetrofitClient.api.getMerma() }, alFallar = { toast("No se pudo cargar el queso de las semanas: ${it.mensaje}") }) { m ->
        val cerradas = m?.semanas.orEmpty()
            .filter { it.cerrada == true && it.stockInicialKg + it.kgProducidos > 0 }
            .take(SEMANAS_ATRAS)
        val pedida = if (semana != null) cerradas.indexOfFirst { it.semana == semana && it.anio == anio } else -1
        when {
            semana != null && pedida < 0 -> toast(
                "La semana $semana no tiene merma para registrar: todavía no ha cerrado (se registra desde el viernes " +
                    "siguiente), no tuvo queso o es muy antigua."
            )
            cerradas.isEmpty() -> toast("Todavía no hay una semana cerrada con queso para registrar la merma")
            else -> {
                val inicial = if (pedida >= 0) pedida else cerradas.indexOfFirst { it.mermaPendiente == true }.takeIf { it >= 0 } ?: 0
                mostrarRegistrarMerma(cerradas, inicial, alGuardar)
            }
        }
    }
}

private fun Fragment.mostrarRegistrarMerma(cerradas: List<CierreSemana>, inicial: Int, alGuardar: () -> Unit) {
    val f = DialogRegistrarMermaBinding.inflate(layoutInflater)
    var i = inicial
    var botonQuitar: TextView? = null

    fun mostrarValor() {
        val s = cerradas[i]
        val kg = Formato.leerNumero(f.etKg.text)
        val precio = s.precioValor
        f.tvValor.text = when {
            kg == null || kg <= 0 -> ""
            precio == null || precio <= 0 -> "Sin ventas ni precio de esa semana: no se puede calcular el valor."
            else -> "Valor de la merma: ${Formato.moneda(kg * precio)} (${Formato.kg(kg)} × ${Formato.moneda(precio)} por kg" +
                (if (s.precioPromedioKg != null) ", precio promedio de venta)" else ", precio registrado)")
        }
        f.tvValor.visibility = if (f.tvValor.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun mostrar() {
        val s = cerradas[i]
        f.tvSemana.text = "Semana ${s.semana}"
        f.tvSemanaDias.text = s.texto ?: ""
        val partes = mutableListOf<String>()
        if (s.stockInicialKg > 0) partes += "Venía de la semana anterior: ${Formato.kg(s.stockInicialKg)}"
        partes += "Producido: ${Formato.kg(s.kgProducidos)}"
        partes += "Vendido: ${Formato.kg(s.kgVendidos)}" +
            if (s.kgVendidosViernesSiguiente > 0) " (${Formato.kg(s.kgVendidosViernesSiguiente)} el viernes siguiente)" else ""
        partes += "Sobró: ${Formato.kg(max(0.0, s.sobranteKg ?: 0.0))}"
        if (s.kgPasados > 0) partes += "Pasaste ${Formato.kg(s.kgPasados)} a la semana siguiente."
        partes += if (s.mermaKg != null) "Merma registrada: ${Formato.kg(s.mermaKg)}" else "Merma pendiente por registrar."
        if (s.completa != true) partes += "Hoy es el viernes siguiente: si todavía vendes queso de esta semana, lo que sobró puede bajar."
        s.revisar?.let { partes += "⚠ $it" }
        f.tvDetalle.text = partes.joinToString("\n")

        val maximo = s.maximoMerma
        f.btnSugerida.text = "Usar lo que sobró: ${Formato.kg(maximo)}"
        f.btnSugerida.setOnClickListener { f.etKg.setText(Formato.campo(maximo)) }
        // Sugerida = lo que sobró (menos lo pasado); si ya está registrada, se muestra la registrada
        f.etKg.setText(Formato.campo(s.mermaKg ?: maximo))
        f.etKg.error = null
        botonQuitar?.visibility = if (s.mermaKg != null) View.VISIBLE else View.GONE
        f.btnSemanaAnterior.isEnabled = i < cerradas.size - 1
        f.btnSemanaSiguiente.isEnabled = i > 0
        f.btnSemanaAnterior.alpha = if (f.btnSemanaAnterior.isEnabled) 1f else 0.3f
        f.btnSemanaSiguiente.alpha = if (f.btnSemanaSiguiente.isEnabled) 1f else 0.3f
        mostrarValor()
    }
    f.btnSemanaAnterior.setOnClickListener { if (i < cerradas.size - 1) { i++; mostrar() } }
    f.btnSemanaSiguiente.setOnClickListener { if (i > 0) { i--; mostrar() } }
    f.etKg.doAfterTextChanged { mostrarValor() }

    val dialogo = mostrarFormulario("Registrar merma", f.root, foco = f.etKg) { dialogo ->
        val s = cerradas[i]
        val kg = Formato.leerNumero(f.etKg.text)
        when {
            kg == null -> { f.etKg.error = "Escribe los kg de merma (0 si no hubo)"; return@mostrarFormulario false }
            kg < 0 -> { f.etKg.error = "Los kg no pueden ser negativos"; return@mostrarFormulario false }
            kg > s.maximoMerma + 0.01 -> {
                f.etKg.error = "Sobraron ${Formato.kg(max(0.0, s.sobranteKg ?: 0.0))}" +
                    (if (s.kgPasados > 0) " y pasaste ${Formato.kg(s.kgPasados)}" else "") +
                    ": la merma puede ser hasta ${Formato.kg(s.maximoMerma)}"
                return@mostrarFormulario false
            }
        }
        val guardar = {
            guardarEnApi(dialogo, "Listo: merma de la semana ${s.semana}: ${Formato.kg(kg!!)}", {
                RetrofitClient.api.registrarMerma(s.anio, s.semana, MermaRequest(kg, Formato.hoyIso()))
            }) { alGuardar() }
        }
        val antes = s.mermaKg
        if (antes == null) {
            guardar()
            false
        } else {
            // Corregir: mostrar qué cambia antes de guardar
            confirmarCambios(listOf(Cambio("Merma (semana ${s.semana})", Formato.kg(antes), Formato.kg(kg!!)))) { guardar() }
        }
    }

    // "Quitar la merma registrada": rojo, arriba y lejos de Guardar; confirma y ofrece Deshacer
    botonQuitar = dialogo.findViewById<TextView>(R.id.btnBorrarFormulario)?.apply {
        text = "Quitar la merma registrada"
        setOnClickListener {
            val s = cerradas[i]
            val kg = s.mermaKg ?: return@setOnClickListener
            dialogo.dismiss()
            confirmarBorrado(
                "Quitar la merma",
                listOf(Dato("Semana", "${s.semana} · ${s.texto ?: ""}"), Dato("Merma", Formato.kg(kg))),
                aviso = "La semana vuelve a quedar «Merma pendiente por registrar».",
                pregunta = "¿Seguro que quieres quitar esta merma?",
                textoBorrar = "Sí, quitar"
            ) { quitarMerma(s, kg, alGuardar) }
        }
    }
    mostrar()
}

private fun Fragment.quitarMerma(s: CierreSemana, kg: Double, alGuardar: () -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        val r = llamarApi { RetrofitClient.api.quitarMerma(s.anio, s.semana) }
        if (view == null) return@launch
        when (r) {
            is Resultado.Exito -> {
                alGuardar()
                val anterior = r.data?.anterior ?: kg
                ofrecerDeshacerCon("Se quitó la merma de la semana ${s.semana}", {
                    RetrofitClient.api.registrarMerma(s.anio, s.semana, MermaRequest(anterior, Formato.hoyIso()))
                }, alGuardar)
            }
            is Resultado.Error -> avisarNoGuardado(r)
        }
    }
}
