package com.queseria.app.ui.liquidaciones

import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.DialogDescuentoBinding
import com.queseria.app.models.Descuento
import com.queseria.app.models.DescuentoRequest
import com.queseria.app.models.Ganadero
import com.queseria.app.models.TipoDescuento
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.toast
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate

/** Hasta cuántas semanas hacia adelante se puede dejar programado un descuento. */
private const val SEMANAS_ADELANTE = 8

/**
 * Botón "Adelanto o descuento" de Inicio: carga los ganaderos activos y abre el formulario
 * para elegir a cuál se le descuenta.
 */
fun Fragment.registrarAdelantoODescuento(alGuardar: (Descuento?) -> Unit = {}) {
    conApi({ RetrofitClient.api.getGanaderos() }) { lista ->
        val activos = lista.orEmpty().filter { it.activo }.sortedBy { it.nombre.lowercase() }
        if (activos.isEmpty()) toast("Primero agrega un ganadero en Ganaderos")
        else abrirFormularioDescuento(null, null, ganaderos = activos, alGuardar = alGuardar)
    }
}

/**
 * Registra un adelanto, préstamo, leche rechazada u otro descuento para un ganadero.
 * El sistema pregunta en qué semana (viernes a jueves) se descuenta: se resta en la liquidación
 * de esa semana. [semana] es la propuesta (por defecto, la actual).
 * Sin [ganaderoId], se elige el ganadero de [ganaderos].
 */
fun Fragment.abrirFormularioDescuento(
    ganaderoId: String?,
    nombreGanadero: String?,
    semana: Semana? = null,
    ganaderos: List<Ganadero> = emptyList(),
    alGuardar: (Descuento?) -> Unit = {}
) {
    val f = DialogDescuentoBinding.inflate(layoutInflater)
    val hoy = LocalDate.now()
    val actual = Semana.actual()
    val ultima = Semana(actual.inicio.plusWeeks(SEMANAS_ADELANTE.toLong()))
    var elegida = semana ?: actual

    // Ganadero: el que viene o uno de la lista
    val elegirGanadero = ganaderoId == null
    if (elegirGanadero) {
        f.lblGanadero.visibility = View.VISIBLE
        f.spGanadero.visibility = View.VISIBLE
        f.spGanadero.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("— Elige el ganadero —") + ganaderos.map { it.nombre }
        )
        if (ganaderos.size == 1) f.spGanadero.setSelection(1)
        f.tvGanadero.visibility = View.GONE
    } else {
        f.tvGanadero.text = nombreGanadero
    }
    fun ganadero(): Pair<String, String>? =
        if (elegirGanadero) ganaderos.getOrNull(f.spGanadero.selectedItemPosition - 1)?.let { it.id to it.nombre }
        else ganaderoId?.let { it to nombreGanadero.orEmpty() }

    /** Fecha con la que se guarda: hoy si cae en la semana; si no, su viernes (futura) o su jueves (pasada). */
    fun fecha(): LocalDate = when {
        elegida.contiene(hoy) -> hoy
        elegida.inicio.isAfter(hoy) -> elegida.inicio
        else -> elegida.fin
    }

    fun mostrarSemana() {
        f.tvSemana.text = "Semana ${elegida.numero}"
        f.tvSemanaDias.text = elegida.textoConDias()
        f.tvAyudaSemana.text = when {
            elegida.esActual -> "Se resta en la liquidación de esta semana."
            elegida.inicio.isAfter(hoy) -> "Semana próxima: se resta cuando se liquide esa semana."
            else -> "Semana ya cerrada: se resta en su liquidación si aún no está pagada."
        }
        f.btnSemanaSiguiente.isEnabled = elegida.inicio.isBefore(ultima.inicio)
        f.btnSemanaSiguiente.alpha = if (f.btnSemanaSiguiente.isEnabled) 1f else 0.3f
    }
    f.btnSemanaAnterior.setOnClickListener { elegida = elegida.anterior(); mostrarSemana() }
    f.btnSemanaSiguiente.setOnClickListener { elegida = elegida.siguiente(); mostrarSemana() }
    mostrarSemana()

    fun esRechazada() = f.rbRechazada.isChecked
    fun refrescarTipo() {
        f.lblValor.text = if (esRechazada()) "Litros rechazados *" else "Valor *"
        f.etValor.hint = if (esRechazada()) "0 L" else "$ 0"
        f.tvAyudaValor.text = if (esRechazada()) "Se descuentan al precio del litro de esa semana." else ""
        f.tvAyudaValor.visibility = if (esRechazada()) View.VISIBLE else View.GONE
    }
    f.rgTipo.setOnCheckedChangeListener { _, _ -> refrescarTipo() }
    refrescarTipo()

    mostrarFormulario("Adelanto o descuento", f.root) { dialogo ->
        val (idGanadero, nombre) = ganadero() ?: run {
            toast("Elige a qué ganadero se le descuenta")
            return@mostrarFormulario false
        }
        val cantidad = Formato.leerNumero(f.etValor.text)
        if (cantidad == null || cantidad <= 0) {
            f.etValor.error = if (esRechazada()) "Ingresa los litros" else "Ingresa el valor"
            return@mostrarFormulario false
        }
        val tipo = when {
            f.rbPrestamo.isChecked -> TipoDescuento.PRESTAMO
            esRechazada() -> TipoDescuento.LECHE_RECHAZADA
            f.rbOtro.isChecked -> TipoDescuento.OTRO
            else -> TipoDescuento.ADELANTO
        }
        val request = DescuentoRequest(
            ganaderoId = idGanadero,
            fecha = fecha().toString(),
            tipo = tipo,
            valor = if (esRechazada()) null else cantidad,
            litros = if (esRechazada()) cantidad else null,
            descripcion = f.etDescripcion.text.toString().trim().ifEmpty { null }
        )
        val mensaje = "${TipoDescuento.legible(tipo)} de $nombre: se descuenta en la semana ${elegida.numero}"
        guardarEnApi(dialogo, mensaje, { RetrofitClient.api.crearDescuento(request) }, alGuardar)
        false
    }
}
