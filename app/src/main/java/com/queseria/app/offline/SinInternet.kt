package com.queseria.app.offline

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.queseria.app.api.Resultado
import com.queseria.app.api.llamarApi
import com.queseria.app.auth.SessionManager
import com.queseria.app.models.ApiResponse
import com.queseria.app.models.Leche
import com.queseria.app.models.LecheRequest
import com.queseria.app.models.Produccion
import com.queseria.app.models.ProduccionRequest
import com.queseria.app.models.Venta
import com.queseria.app.models.VentaRequest
import com.queseria.app.ui.common.guardarDesdeDialogo
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.common.preguntar
import com.queseria.app.ui.common.toast
import retrofit2.Response
import java.util.Calendar
import java.util.UUID

/** Identificador del teléfono para un registro nuevo (el servidor no guarda dos con el mismo). */
fun nuevoIdLocal(): String = UUID.randomUUID().toString()

private val gson = Gson()
private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "19 nov, 9:40 p. m." */
fun textoHora(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val h = c.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val m = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    val ampm = if (c.get(Calendar.AM_PM) == Calendar.AM) "a. m." else "p. m."
    return "${c.get(Calendar.DAY_OF_MONTH)} ${MESES[c.get(Calendar.MONTH)]}, $h:$m $ampm"
}

/**
 * Guarda un registro nuevo (leche, producción o venta). Con internet es igual que antes; si no hay
 * conexión, lo deja en el teléfono ([Pendientes]) y se sube solo cuando vuelva (una sola vez gracias a su idLocal).
 * [llamada] recibe confirmar (semana ya pagada, solo leche). [pendiente] es el registro tal como se subiría.
 */
fun <T> Fragment.guardarOEnCola(
    dialogo: Dialog,
    mensajeExito: String,
    pendiente: Pendiente,
    llamada: suspend (confirmar: Boolean) -> Response<ApiResponse<T>>,
    alGuardar: () -> Unit = {}
) {
    guardarDesdeDialogo(dialogo, {
        var confirmado = false
        var r = llamarApi { llamada(false) }
        if (r is Resultado.Error && r.requiereConfirmacion && preguntar("Semana ya pagada", r.mensaje, "Guardar igual")) {
            confirmado = true
            r = llamarApi { llamada(true) }
        }
        when {
            r is Resultado.Exito -> { toast(mensajeExito); true }
            r is Resultado.Error && r.sinConexion -> {
                Pendientes.agregar(requireContext(), pendiente.copy(confirmado = confirmado))
                AlertDialog.Builder(requireContext())
                    .setTitle("Guardado en el teléfono")
                    .setMessage("No hay internet: quedó en el teléfono como «pendiente» y se sube cuando vuelva " +
                        "la conexión, sin que tengas que hacer nada.\n\n${pendiente.descripcion.replaceFirstChar { it.uppercase() }}")
                    .setPositiveButton("Entendido", null)
                    .show()
                true
            }
            r is Resultado.Error && r.requiereConfirmacion -> false
            r is Resultado.Error -> { manejarError(r); false }
            else -> false
        }
    }) { alGuardar() }
}

/** Arma el registro pendiente (con su idLocal y la cuenta actual). */
fun Fragment.pendiente(tipo: String, idLocal: String, cuerpo: Any, fecha: String, descripcion: String, nombre: String?) = Pendiente(
    idLocal = idLocal,
    tipo = tipo,
    cuerpo = gson.toJson(cuerpo),
    fecha = fecha,
    descripcion = descripcion,
    nombre = nombre,
    usuario = SessionManager(requireContext()).obtenerUsername()
)

/** Pendientes de la cuenta actual de un tipo, sin los que el servidor ya devolvió (mismo idLocal). */
fun Fragment.pendientesDe(tipo: String, yaEnServidor: Set<String?> = emptySet()): List<Pendiente> {
    val ctx = context ?: return emptyList()
    return Pendientes.de(ctx, SessionManager(ctx).obtenerUsername(), tipo).filter { it.idLocal !in yaEnServidor }
}

fun Pendiente.comoLeche(): Leche {
    val r = gson.fromJson(cuerpo, LecheRequest::class.java)
    return Leche(fecha = r.fecha ?: fecha, litros = r.litros, ganaderoId = r.ganaderoId, nombreGanadero = nombre,
        observaciones = r.observaciones, pendiente = this)
}

fun Pendiente.comoProduccion(): Produccion {
    val r = gson.fromJson(cuerpo, ProduccionRequest::class.java)
    return Produccion(fecha = r.fecha ?: fecha, litrosUsados = r.litrosUsados, kgProducidos = r.kgProducidos,
        observaciones = r.observaciones, productoId = r.productoId, pendiente = this)
}

fun Pendiente.comoVenta(): Venta {
    val r = gson.fromJson(cuerpo, VentaRequest::class.java)
    return Venta(fecha = r.fecha ?: fecha, tipoVenta = r.tipoVenta, cliente = nombre ?: r.cliente, clienteId = r.clienteId,
        productoId = r.productoId, cantidad = r.cantidad, unidad = r.unidad, precioUnitario = r.precioUnitario,
        totalVenta = r.cantidad * r.precioUnitario, fiada = r.fiada, pendiente = this)
}

/** Texto corto de la etiqueta en las listas. */
val Pendiente.etiqueta: String get() = if (rechazado) "No se subió" else "Pendiente"

/**
 * Detalle de un registro que no ha llegado al servidor. Pendiente: "Subir ahora". Rechazado: dice por qué y
 * deja reintentar (si la semana ya está pagada, pregunta antes). Nunca se borra desde aquí.
 */
fun Context.mostrarPendiente(p: Pendiente) {
    val estado = if (p.rechazado) "El servidor no lo aceptó:\n${p.error}" else "Se subirá solo cuando haya internet."
    val b = AlertDialog.Builder(this)
        .setTitle(if (p.rechazado) "No se pudo subir" else "Pendiente de subir")
        .setMessage("${p.descripcion.replaceFirstChar { it.uppercase() }}\nHecho el ${textoHora(p.creado)} sin internet.\n\n$estado" +
            if (p.rechazado) "\n\nNo se borró: corrige lo que dice el mensaje (por ejemplo, registra la producción que falta) y toca «Reintentar»." else "")
        .setNegativeButton("Cerrar", null)
    if (p.rechazado) {
        b.setPositiveButton(if (p.requiereConfirmacion) "Guardar igual" else "Reintentar") { _, _ ->
            Sincronizador.reintentar(this, p, confirmado = p.requiereConfirmacion)
        }
    } else {
        b.setPositiveButton("Subir ahora") { _, _ -> Sincronizador.subir(this) }
    }
    b.show()
}

/** Lista de todo lo que falta por subir (desde el aviso superior). */
fun Context.mostrarListaPendientes() {
    val lista = Pendientes.de(this, SessionManager(this).obtenerUsername()).sortedBy { it.creado }
    if (lista.isEmpty()) return
    val textos = lista.map { (if (it.rechazado) "⚠ No se subió · " else "Pendiente · ") + it.descripcion }.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle("Registros hechos sin internet")
        .setItems(textos) { _, i -> mostrarPendiente(lista[i]) }
        .setPositiveButton("Subir ahora") { _, _ -> Sincronizador.subir(this) }
        .setNegativeButton("Cerrar", null)
        .show()
}

/**
 * Para lo que solo funciona con internet (liquidación, contraseña…): si el teléfono no tiene red, lo dice
 * claramente y devuelve true (no se intenta).
 */
fun Fragment.necesitaInternet(que: String, porque: String): Boolean {
    val ctx = context ?: return true
    if (Conexion.hayRed(ctx)) return false
    AlertDialog.Builder(ctx)
        .setTitle("Necesita internet")
        .setMessage("$que necesita internet: $porque.\nConéctate y vuelve a intentarlo.")
        .setPositiveButton("Entendido", null)
        .show()
    return true
}
