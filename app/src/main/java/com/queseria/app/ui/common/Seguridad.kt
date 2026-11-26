package com.queseria.app.ui.common

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.DialogRevisarBinding
import com.queseria.app.models.ApiResponse
import com.queseria.app.models.Borrado
import com.queseria.app.models.ConfirmarRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import retrofit2.Response

/*
 * Seguridad al modificar o borrar (leche, producción, ventas, precios, adelantos y liquidaciones):
 * - Borrar va aparte y en rojo (en el formulario, arriba; Guardar está abajo).
 * - Antes de borrar se muestra el registro y se pregunta "¿Seguro que quieres borrarlo?"; el botón
 *   resaltado es "Cancelar".
 * - Antes de guardar una corrección se muestra qué cambia ("Litros: 40 → 45").
 * - Después de borrar aparece "Deshacer" unos segundos; lo borrado queda 30 días en la Papelera.
 */

/** Cuánto dura el aviso con "Deshacer". */
private const val SEGUNDOS_DESHACER = 8

/** Un dato del registro: "Litros" → "45 L". */
data class Dato(val nombre: String, val valor: String)

/** Un campo corregido. Solo se muestran los que cambian. */
data class Cambio(val campo: String, val antes: String, val despues: String) {
    val cambia: Boolean get() = antes.trim() != despues.trim()
}

/** Diálogo de revisión común (dialog_revisar.xml). Devuelve el diálogo ya armado (sin mostrar). */
private fun Fragment.armarRevision(
    titulo: String,
    filas: List<Pair<String, CharSequence>>,
    aviso: String?,
    pregunta: String
): Pair<AlertDialog, DialogRevisarBinding> {
    val ctx = requireContext()
    val b = DialogRevisarBinding.inflate(layoutInflater)
    b.tvTitulo.text = titulo
    val negrita = ResourcesCompat.getFont(ctx, R.font.poppins_semibold)
    filas.forEach { (nombre, valor) ->
        val fila = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(5))
        }
        fila.addView(TextView(ctx).apply {
            text = nombre
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto_secundario))
            layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        fila.addView(TextView(ctx).apply {
            text = valor
            textSize = 15f
            setTypeface(negrita, Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(ctx, R.color.gris_texto_principal))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        b.contenedorDatos.addView(fila)
    }
    b.tvAviso.text = aviso
    b.tvAviso.visibility = if (aviso.isNullOrBlank()) View.GONE else View.VISIBLE
    b.tvPregunta.text = pregunta
    val dialogo = AlertDialog.Builder(ctx).setView(b.root).create()
    return dialogo to b
}

/**
 * "¿Seguro que quieres borrarlo?" mostrando el registro ([datos]: fecha, nombre, cantidad, valor).
 * El botón resaltado (verde) es "Cancelar"; el de borrar va abajo, en rojo y con borde.
 */
fun Fragment.confirmarBorrado(
    titulo: String,
    datos: List<Dato>,
    aviso: String? = null,
    pregunta: String = "¿Seguro que quieres borrarlo?",
    textoBorrar: String = "Sí, borrar",
    textoCancelar: String = "Cancelar, no borrar",
    alBorrar: () -> Unit
) {
    val (dialogo, b) = armarRevision(titulo, datos.map { it.nombre to it.valor }, aviso, pregunta)
    b.btnPrincipal.text = textoCancelar
    b.btnPrincipal.setIconResource(R.drawable.ic_cerrar)
    b.btnPrincipal.setOnClickListener { dialogo.dismiss() }
    b.btnSecundario.text = textoBorrar
    b.btnSecundario.setTextColor(ContextCompat.getColor(requireContext(), R.color.rojo_error))
    b.btnSecundario.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.rojo_error)
    b.btnSecundario.setIconResource(R.drawable.ic_eliminar)
    b.btnSecundario.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.rojo_error)
    b.btnSecundario.setOnClickListener { dialogo.dismiss(); alBorrar() }
    dialogo.show()
    // El botón que queda listo para tocar (teclado/accesibilidad) es Cancelar
    b.btnPrincipal.requestFocus()
}

/**
 * Antes de guardar una corrección: muestra qué cambia ("Litros: 40 → 45") y pide confirmar.
 * Si nada cambió, lo dice y no guarda. Devuelve false (el formulario sigue abierto hasta que se guarde).
 */
fun Fragment.confirmarCambios(cambios: List<Cambio>, aviso: String? = null, alConfirmar: () -> Unit): Boolean {
    val reales = cambios.filter { it.cambia }
    if (reales.isEmpty()) {
        toast("No cambiaste nada")
        return false
    }
    val verde = ContextCompat.getColor(requireContext(), R.color.verde_oscuro)
    val filas = reales.map { c ->
        c.campo to android.text.SpannableStringBuilder().apply {
            append(c.antes.ifBlank { "(vacío)" })
            append("  →  ")
            val inicio = length
            append(c.despues.ifBlank { "(vacío)" })
            setSpan(android.text.style.ForegroundColorSpan(verde), inicio, length, 0)
        }
    }
    val (dialogo, b) = armarRevision("Revisa los cambios", filas, aviso, "¿Guardar estos cambios?")
    b.btnPrincipal.text = "Sí, guardar cambios"
    b.btnPrincipal.setOnClickListener { dialogo.dismiss(); alConfirmar() }
    b.btnSecundario.text = "Volver a editar"
    b.btnSecundario.setOnClickListener { dialogo.dismiss() }
    dialogo.show()
    return false
}

/**
 * Borra con [llamada] (si toca una semana ya pagada, pregunta y repite con confirmar = true), avisa con
 * [mensaje] y ofrece "Deshacer" unos segundos. [alTerminar] se llama al borrar y al deshacer (para recargar).
 */
fun Fragment.borrarConDeshacer(
    mensaje: String,
    llamada: suspend (confirmar: Boolean) -> Response<ApiResponse<Borrado>>,
    /** Dónde mostrar "Deshacer" si se borra desde una ventana que sigue abierta (si no, la pantalla). */
    enVentana: () -> View? = { null },
    alTerminar: () -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        var r = llamarApi { llamada(false) }
        if (r is Resultado.Error && r.requiereConfirmacion && preguntar("Semana ya pagada", r.mensaje, "Borrar igual")) {
            r = llamarApi { llamada(true) }
        }
        if (view == null) return@launch
        when (r) {
            is Resultado.Exito -> {
                alTerminar()
                ofrecerDeshacer(mensaje, r.data?.papeleraId, enVentana(), alTerminar)
            }
            is Resultado.Error -> if (!r.requiereConfirmacion) avisarNoGuardado(r)
        }
    }
}

/**
 * Aviso abajo con el botón "Deshacer" (restaura desde la papelera). Funciona aunque la pantalla ya se haya
 * cerrado (la restauración va ligada a la actividad); [alDeshacer] solo se llama si la pantalla sigue abierta.
 * Sin papelera (servidor viejo), solo el aviso. [ventana]: vista de una ventana abierta encima de la pantalla
 * (por ejemplo, los abonos de una venta), para que el aviso no quede tapado.
 */
fun Fragment.ofrecerDeshacer(mensaje: String, papeleraId: String?, ventana: View? = null, alDeshacer: () -> Unit) {
    val actividad = activity ?: return
    val raiz = ventana?.takeIf { it.isAttachedToWindow } ?: actividad.findViewById<View>(android.R.id.content) ?: return
    if (papeleraId == null) {
        toast(mensaje)
        return
    }
    val fragmento = this
    val aviso = Snackbar.make(raiz, "$mensaje · queda 30 días en la Papelera", SEGUNDOS_DESHACER * 1000)
        .setAction("Deshacer") {
            actividad.restaurarDePapelera(papeleraId, "Listo: se recuperó") { if (fragmento.view != null) alDeshacer() }
        }
        .setActionTextColor(ContextCompat.getColor(actividad, R.color.dorado_titulo))
    // Encima de la barra inferior (si está a la vista y el aviso va en la pantalla)
    if (raiz !== ventana) actividad.findViewById<View>(R.id.barraInferior)?.takeIf { it.isShown }?.let { aviso.setAnchorView(it) }
    aviso.show()
}

/**
 * Aviso con "Deshacer" para cambios que no pasan por la papelera (por ejemplo, quitar la merma de una semana):
 * [deshacer] vuelve a dejar lo de antes (ligado a la actividad, sirve aunque se cierre la pantalla);
 * [alDeshacer] se llama si salió bien y la pantalla sigue abierta.
 */
fun <T> Fragment.ofrecerDeshacerCon(
    mensaje: String,
    deshacer: suspend () -> Response<ApiResponse<T>>,
    alDeshacer: () -> Unit
) {
    val actividad = activity ?: return
    val raiz = actividad.findViewById<View>(android.R.id.content) ?: return
    val fragmento = this
    val aviso = Snackbar.make(raiz, mensaje, SEGUNDOS_DESHACER * 1000)
        .setAction("Deshacer") {
            actividad.lifecycleScope.launch {
                when (val r = llamarApi { deshacer() }) {
                    is Resultado.Exito -> {
                        Toast.makeText(actividad, "Listo: se deshizo", Toast.LENGTH_SHORT).show()
                        if (fragmento.view != null) alDeshacer()
                    }
                    is Resultado.Error -> AlertDialog.Builder(actividad).setTitle("No se deshizo")
                        .setMessage(if (r.sinConexion) "Necesita internet. Vuelve a registrarlo cuando tengas conexión." else r.mensaje)
                        .setPositiveButton("Entendido", null).show()
                }
            }
        }
        .setActionTextColor(ContextCompat.getColor(actividad, R.color.dorado_titulo))
    actividad.findViewById<View>(R.id.barraInferior)?.takeIf { it.isShown }?.let { aviso.setAnchorView(it) }
    aviso.show()
}

/** Restaura una entrada de la papelera desde una pantalla ([alTerminar] solo si la pantalla sigue abierta). */
fun Fragment.restaurarDePapelera(papeleraId: String, mensaje: String, alTerminar: () -> Unit) {
    val actividad = activity ?: return
    val fragmento = this
    actividad.restaurarDePapelera(papeleraId, mensaje) { if (fragmento.view != null) alTerminar() }
}

/** Restaura una entrada de la papelera (pregunta si toca una semana ya pagada). */
fun FragmentActivity.restaurarDePapelera(papeleraId: String, mensaje: String, alTerminar: () -> Unit) {
    val actividad = this
    lifecycleScope.launch {
        var r = llamarApi { RetrofitClient.api.restaurarPapelera(papeleraId, ConfirmarRequest()) }
        if (r is Resultado.Error && r.requiereConfirmacion && actividad.preguntarEn("Semana ya pagada", r.mensaje, "Recuperar igual")) {
            r = llamarApi { RetrofitClient.api.restaurarPapelera(papeleraId, ConfirmarRequest(confirmar = true)) }
        }
        if (actividad.isFinishing) return@launch
        when (r) {
            is Resultado.Exito -> { Toast.makeText(actividad, mensaje, Toast.LENGTH_SHORT).show(); alTerminar() }
            is Resultado.Error -> if (!r.requiereConfirmacion) {
                val texto = if (r.sinConexion) "No se pudo recuperar: necesita internet. Búscalo luego en la Papelera." else r.mensaje
                AlertDialog.Builder(actividad).setTitle("No se recuperó").setMessage(texto).setPositiveButton("Entendido", null).show()
            }
        }
    }
}

private suspend fun FragmentActivity.preguntarEn(titulo: String, mensaje: String, textoSi: String): Boolean =
    suspendCancellableCoroutine { cont ->
        val dialogo = AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(textoSi) { _, _ -> if (cont.isActive) cont.resume(true) }
            .setNegativeButton("Cancelar") { _, _ -> if (cont.isActive) cont.resume(false) }
            .setOnCancelListener { if (cont.isActive) cont.resume(false) }
            .show()
        cont.invokeOnCancellation { dialogo.dismiss() }
    }
