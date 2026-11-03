package com.queseria.app.ui.common

import android.app.Dialog
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.llamarApi
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.CabeceraIlustradaBinding
import com.queseria.app.databinding.FormularioCompletoBinding
import com.queseria.app.models.ApiResponse
import com.queseria.app.ui.auth.LoginActivity
import com.queseria.app.util.Formato
import retrofit2.Response
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Funciones de ayuda compartidas por los fragments.
 */

/**
 * Hace una llamada a la API ligada a la vista del fragment.
 * Si sale bien llama a [alExito] (solo si la vista sigue abierta); si falla muestra el error
 * (y vuelve al login si la sesión expiró) y llama a [alFallar].
 * Si se pasa [refresco], muestra su indicador de carga mientras dura la llamada.
 */
fun <T> Fragment.conApi(
    llamada: suspend () -> Response<ApiResponse<T>>,
    refresco: SwipeRefreshLayout? = null,
    alFallar: (Resultado.Error) -> Unit = {},
    alExito: (T?) -> Unit
) {
    refresco?.isRefreshing = true
    viewLifecycleOwner.lifecycleScope.launch {
        val r = llamarApi(llamada)
        if (view == null) return@launch
        refresco?.isRefreshing = false
        when (r) {
            is Resultado.Exito -> alExito(r.data)
            is Resultado.Error -> { manejarError(r); alFallar(r) }
        }
    }
}

/**
 * Versión para formularios: [guardarDesdeDialogo] con una llamada a la API.
 * Cierra el diálogo y muestra [mensajeExito] solo si el backend aceptó los datos.
 */
fun <T> Fragment.guardarEnApi(
    dialogo: Dialog,
    mensajeExito: String,
    llamada: suspend () -> Response<ApiResponse<T>>,
    alGuardar: (T?) -> Unit = {}
) {
    var dato: T? = null
    guardarDesdeDialogo(dialogo, {
        when (val r = llamarApi(llamada)) {
            is Resultado.Exito -> { dato = r.data; toast(mensajeExito); true }
            is Resultado.Error -> { manejarError(r); false }
        }
    }) { alGuardar(dato) }
}

/**
 * Como [guardarEnApi], para cambios que pueden tocar una liquidación ya pagada:
 * [llamada] recibe confirmar = false; si el backend pide confirmación (409), se muestra su aviso
 * y, si el usuario acepta, se repite con confirmar = true.
 */
fun <T> Fragment.guardarConConfirmacion(
    dialogo: Dialog,
    mensajeExito: String,
    llamada: suspend (confirmar: Boolean) -> Response<ApiResponse<T>>,
    alGuardar: (T?) -> Unit = {}
) {
    var dato: T? = null
    guardarDesdeDialogo(dialogo, {
        var r = llamarApi { llamada(false) }
        if (r is Resultado.Error && r.requiereConfirmacion && preguntar("Semana ya pagada", r.mensaje, "Guardar igual")) {
            r = llamarApi { llamada(true) }
        }
        when (r) {
            is Resultado.Exito -> { dato = r.data; toast(mensajeExito); true }
            is Resultado.Error -> { if (!r.requiereConfirmacion) manejarError(r); false }
        }
    }) { alGuardar(dato) }
}

/** Diálogo Sí/Cancelar que se puede esperar desde una corrutina: true si el usuario aceptó. */
suspend fun Fragment.preguntar(titulo: String, mensaje: String, textoSi: String): Boolean =
    suspendCancellableCoroutine { cont ->
        val dialogo = AlertDialog.Builder(requireContext())
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(textoSi) { _, _ -> if (cont.isActive) cont.resume(true) }
            .setNegativeButton("Cancelar") { _, _ -> if (cont.isActive) cont.resume(false) }
            .setOnCancelListener { if (cont.isActive) cont.resume(false) }
            .show()
        cont.invokeOnCancellation { dialogo.dismiss() }
    }

fun Fragment.toast(mensaje: String) {
    context?.let { Toast.makeText(it, mensaje, Toast.LENGTH_SHORT).show() }
}

/**
 * Muestra el error de una llamada al backend.
 * Si la sesión expiró (401), cierra sesión y vuelve al login.
 */
fun Fragment.manejarError(error: Resultado.Error) {
    val ctx = context ?: return
    toast(error.mensaje)
    if (error.sesionExpirada) {
        SessionManager(ctx).cerrarSesion()
        val intent = Intent(ctx, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }
}

/** Diálogo de confirmación (por ejemplo, antes de eliminar). */
fun Fragment.confirmar(titulo: String, mensaje: String, textoSi: String = "Eliminar", alConfirmar: () -> Unit) {
    AlertDialog.Builder(requireContext())
        .setTitle(titulo)
        .setMessage(mensaje)
        .setPositiveButton(textoSi) { _, _ -> alConfirmar() }
        .setNegativeButton("Cancelar", null)
        .show()
}

/**
 * Muestra un formulario a pantalla completa (guía de diseño, formulario_completo.xml): cabecera verde
 * ilustrada con el [titulo] (la última palabra en dorado), el formulario [vista] y los botones
 * Cancelar y Guardar ([textoGuardar]) abajo. [ilustracion] es la imagen de la cabecera (si es null,
 * se elige según el título).
 * [alGuardar] devuelve true si los datos son válidos y el formulario se puede cerrar;
 * si devuelve false, queda abierto para que el usuario corrija.
 * [foco] es el campo que recibe el cursor al abrir (con el teclado ya abierto, numérico si el campo lo es).
 */
fun Fragment.mostrarFormulario(
    titulo: String,
    vista: View,
    textoGuardar: String = "Guardar",
    foco: View? = null,
    @DrawableRes ilustracion: Int? = null,
    alGuardar: (dialogo: Dialog) -> Boolean
): Dialog {
    val dialogo = Dialog(requireContext(), R.style.Theme_QuesoData_Formulario)
    val marco = FormularioCompletoBinding.inflate(layoutInflater)
    // Los formularios vienen dentro de un ScrollView propio: aquí ya hay uno, se usa solo su contenido
    val contenido = (vista as? ScrollView)?.getChildAt(0)?.also { vista.removeView(it) } ?: vista
    marco.contenidoFormulario.addView(contenido)
    marco.cabecera.mostrar(titulo, ilustracion ?: ilustracionDe(titulo)) { dialogo.dismiss() }
    marco.btnGuardarFormulario.text = textoGuardar
    marco.btnGuardarFormulario.setOnClickListener { if (alGuardar(dialogo)) dialogo.dismiss() }
    marco.btnCancelarFormulario.setOnClickListener { dialogo.dismiss() }
    dialogo.setContentView(marco.root)
    dialogo.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    if (foco != null) {
        dialogo.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        foco.requestFocus()
    }
    dialogo.show()
    return dialogo
}

/**
 * Llena una cabecera ilustrada (cabecera_ilustrada.xml): el [titulo] con la última palabra en dorado
 * debajo ("Registrar" / "producción"), la [ilustracion] y lo que hace el botón atrás.
 */
fun CabeceraIlustradaBinding.mostrar(titulo: String, @DrawableRes ilustracion: Int, alVolver: () -> Unit) {
    val corte = titulo.lastIndexOf(' ')
    tvTitulo.text = if (corte <= 0) titulo else SpannableString(titulo.substring(0, corte) + "\n" + titulo.substring(corte + 1)).apply {
        val dorado = ContextCompat.getColor(root.context, R.color.dorado_titulo)
        setSpan(ForegroundColorSpan(dorado), corte + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    ivIlustracion.setImageResource(ilustracion)
    btnAtras.setOnClickListener { alVolver() }
}

/** Ilustración de la cabecera según el formulario. */
@DrawableRes
fun ilustracionDe(titulo: String): Int {
    val t = titulo.lowercase()
    return when {
        "producción" in t -> R.drawable.img_guia_leche
        "leche" in t || "pagado" in t -> R.drawable.img_liquidacion_cantina
        "venta" in t -> R.drawable.img_nueva_venta_queso
        "precio" in t -> R.drawable.img_precios_quesos
        "ganadero" in t -> R.drawable.img_ganaderos_leche
        "cliente" in t -> R.drawable.img_clientes_apreton
        "producto" in t -> R.drawable.img_productos_queso
        "movimiento" in t || "inventario" in t || "cierre" in t -> R.drawable.img_inventario_estante
        else -> R.drawable.img_guia_quesos
    }
}

/**
 * Abre el calendario (estilo de la guía de diseño, ver Calendario.kt). [fechaIso] es la fecha
 * inicial (yyyy-MM-dd); [alElegir] recibe la fecha elegida en formato ISO.
 */
fun Fragment.elegirFecha(fechaIso: String?, alElegir: (String) -> Unit) {
    val inicial = fechaIso?.let { runCatching { LocalDate.parse(Formato.soloFecha(it)) }.getOrNull() } ?: LocalDate.now()
    mostrarCalendario(requireContext(), inicial) { alElegir(it.toString()) }
}

/**
 * Ejecuta un guardado desde un formulario con el botón "Guardar" deshabilitado
 * (evita registros duplicados por doble toque). Si [accion] devuelve true, cierra el
 * formulario y llama a [alGuardar].
 */
fun Fragment.guardarDesdeDialogo(dialogo: Dialog, accion: suspend () -> Boolean, alGuardar: () -> Unit = {}) {
    val boton = dialogo.findViewById<View>(R.id.btnGuardarFormulario)
        ?: (dialogo as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
    boton.isEnabled = false
    lifecycleScope.launch {
        val ok = accion()
        boton.isEnabled = true
        if (ok) {
            dialogo.dismiss()
            if (view != null) alGuardar()
        }
    }
}
