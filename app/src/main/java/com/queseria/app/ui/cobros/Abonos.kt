package com.queseria.app.ui.cobros

import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.DialogAbonoBinding
import com.queseria.app.databinding.DialogCuentaVentaBinding
import com.queseria.app.models.Abono
import com.queseria.app.models.AbonoRequest
import com.queseria.app.models.CuentaPorCobrar
import com.queseria.app.models.Venta
import com.queseria.app.ui.common.Cambio
import com.queseria.app.ui.common.Dato
import com.queseria.app.ui.common.Tabla
import com.queseria.app.ui.common.borrarConDeshacer
import com.queseria.app.ui.common.cantidadCorta
import com.queseria.app.ui.common.confirmarBorrado
import com.queseria.app.ui.common.confirmarCambios
import com.queseria.app.ui.common.elegirFecha
import com.queseria.app.ui.common.fechaConMes
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.ventas.total
import com.queseria.app.util.Formato
import kotlinx.coroutines.launch

/*
 * Abonos a las ventas fiadas: la ventana de una venta (total, abonado, lo que falta y la tabla de abonos) y el
 * formulario para registrar o corregir un abono. Los abonos necesitan internet (no van a la cola sin conexión).
 * Corregir muestra los cambios antes de guardar; borrar pide confirmación y ofrece "Deshacer" (Papelera).
 */

/** Lo que hace falta de una venta para mostrar y registrar sus abonos. */
data class VentaCobro(
    val ventaId: String,
    val cliente: String,
    val fecha: String,
    val cantidad: Double,
    val unidad: String,
    val total: Double
)

fun Venta.comoCobro(): VentaCobro? = id?.let {
    VentaCobro(it, cliente?.takeIf { c -> c.isNotBlank() } ?: "Cliente", fecha, cantidad, unidad, total)
}

fun CuentaPorCobrar.comoCobro() =
    VentaCobro(ventaId, cliente ?: "Cliente", fecha, cantidad, unidad, total)

/** Abonos de una venta: Fecha (fija) | Valor | Nota, con el total abonado. */
fun tablaAbonos(alTocar: (Abono) -> Unit): Tabla<Abono> = Tabla(
    columnas = listOf(
        Tabla.Columna("Fecha", 92),
        Tabla.Columna("Valor", 116, numero = true),
        Tabla.Columna("Nota", 170)
    ),
    celdas = { a -> listOf(fechaConMes(a.fecha), Formato.moneda(a.valor), a.nota?.takeIf { it.isNotBlank() } ?: "—") },
    alTocar = alTocar
)

/**
 * Ventana de los abonos de la venta [v]: lo que falta, la tabla de abonos y "Registrar abono".
 * [alCambiar] se llama cada vez que se guarda, corrige, borra o recupera un abono (para recargar la lista de atrás).
 */
fun Fragment.abrirAbonos(v: VentaCobro, alCambiar: () -> Unit) {
    val b = DialogCuentaVentaBinding.inflate(layoutInflater)
    b.tvCliente.text = v.cliente
    b.tvDetalle.text = "Venta del ${Formato.fechaLegible(v.fecha)} · ${cantidadCorta(v.cantidad, v.unidad)}"
    b.tvTotal.text = Formato.moneda(v.total)
    var abonos: List<Abono> = emptyList()
    var saldo = v.total
    lateinit var cargar: () -> Unit
    var ventana: android.app.Dialog? = null
    // "Deshacer" (al borrar un abono) se muestra dentro de esta ventana, que sigue abierta
    val tabla = tablaAbonos { a ->
        abrirFormularioAbono(v, saldo + a.valor, a, { ventana?.findViewById(android.R.id.content) }) { cargar(); alCambiar() }
    }

    cargar = {
        viewLifecycleOwner.lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.getAbonos(v.ventaId) }
            if (view == null) return@launch
            when (r) {
                is Resultado.Exito -> {
                    abonos = r.data.orEmpty()
                    val abonado = abonos.sumOf { it.valor }
                    saldo = (v.total - abonado).coerceAtLeast(0.0)
                    b.tvAbonado.text = Formato.moneda(abonado)
                    b.tvFalta.text = Formato.moneda(saldo)
                    b.tvFalta.setTextColor(androidx.core.content.ContextCompat.getColor(b.root.context,
                        if (saldo < 0.5) R.color.verde_principal else R.color.ambar_advertencia))
                    b.tvEstado.text = if (saldo < 0.5) "Pagada: ya te pagaron toda la venta."
                        else "Pendiente por pagar: faltan ${Formato.moneda(saldo)}."
                    tabla.llenarEn(b.tablaAbonos, abonos,
                        if (abonos.isEmpty()) null else listOf("Abonado", Formato.moneda(abonado), plural(abonos.size)),
                        "Todavía no hay abonos.")
                    b.tvAyuda.visibility = if (abonos.isEmpty()) View.GONE else View.VISIBLE
                }
                is Resultado.Error -> {
                    manejarError(r)
                    b.tvEstado.text = "No se pudieron cargar los abonos: ${r.mensaje}"
                }
            }
        }
    }

    val dialogo = mostrarFormulario("Abonos de la venta", b.root, textoGuardar = "Abonar",
        ilustracion = R.drawable.img_nueva_venta_queso) {
        if (saldo < 0.5) {
            b.tvEstado.text = "Esta venta ya está pagada: no le falta nada."
        } else {
            abrirFormularioAbono(v, saldo, null) { cargar(); alCambiar() }
        }
        false
    }
    dialogo.findViewById<TextView>(R.id.btnCancelarFormulario)?.text = "Cerrar"
    ventana = dialogo
    cargar()
}

private fun plural(n: Int) = if (n == 1) "1 abono" else "$n abonos"

/**
 * Registrar ([existente] = null) o corregir un abono. [maximo] = lo que el abono puede valer como mucho
 * (lo que falta; al corregir, lo que falta más el valor de este abono). [alGuardar]: después de guardar o borrar.
 */
fun Fragment.abrirFormularioAbono(
    v: VentaCobro,
    maximo: Double,
    existente: Abono?,
    /** Ventana abierta donde mostrar "Deshacer" al borrar (la de los abonos de la venta). */
    ventanaDeshacer: () -> View? = { null },
    alGuardar: () -> Unit
) {
    val f = DialogAbonoBinding.inflate(layoutInflater)
    var fecha = existente?.let { Formato.soloFecha(it.fecha) } ?: Formato.hoyIso()
    // Un abono no puede ser de antes de la venta
    if (fecha < Formato.soloFecha(v.fecha)) fecha = Formato.soloFecha(v.fecha)
    f.tvFecha.text = Formato.fechaLegible(fecha)
    f.tvFecha.setOnClickListener {
        elegirFecha(fecha) { nueva ->
            if (nueva < Formato.soloFecha(v.fecha)) {
                f.tvVenta.text = "El abono no puede ser de antes de la venta (${Formato.fechaLegible(v.fecha)})."
            } else {
                fecha = nueva
                f.tvFecha.text = Formato.fechaLegible(nueva)
            }
        }
    }
    f.tvVenta.text = "${v.cliente} · venta del ${Formato.fechaLegible(v.fecha)} por ${Formato.moneda(v.total)}.\n" +
        if (existente == null) "Falta: ${Formato.moneda(maximo)}" else "Sin este abono faltan ${Formato.moneda(maximo)}"
    f.btnTodo.text = "Todo lo que falta: ${Formato.moneda(maximo)}"
    f.btnTodo.setOnClickListener { f.etValor.setText(Math.round(maximo).toString()) }
    existente?.let {
        f.etValor.setText(Formato.campo(it.valor))
        f.etNota.setText(it.nota.orEmpty())
    }

    val alBorrar: (() -> Unit)? = existente?.id?.let { id ->
        {
            confirmarBorrado("Borrar abono", listOf(
                Dato("Cliente", v.cliente),
                Dato("Fecha", Formato.fechaLegible(existente.fecha)),
                Dato("Valor", Formato.moneda(existente.valor))
            ), aviso = "La venta vuelve a deber ese valor.") {
                borrarConDeshacer("Abono borrado", { _ -> RetrofitClient.api.eliminarAbono(id) }, ventanaDeshacer) { alGuardar() }
            }
        }
    }

    mostrarFormulario(if (existente == null) "Registrar abono" else "Corregir abono", f.root, foco = f.etValor,
        ilustracion = R.drawable.img_nueva_venta_queso, alBorrar = alBorrar, textoBorrar = "Borrar este abono") { dialogo ->
        val (valor, error) = Formato.validarPositivo(f.etValor.text, "Escribe el valor del abono", "El valor")
        if (valor == null) {
            f.etValor.error = error
            return@mostrarFormulario false
        }
        if (valor > maximo + 0.5) {
            f.etValor.error = "Solo falta ${Formato.moneda(maximo)}: el abono no puede ser mayor"
            return@mostrarFormulario false
        }
        val nota = f.etNota.text.toString().trim()
        val request = AbonoRequest(ventaId = v.ventaId, fecha = fecha, valor = valor, nota = nota)
        val id = existente?.id
        if (id == null) {
            val queda = maximo - valor
            guardarEnApi(dialogo, if (queda < 0.5) "Listo: venta pagada completa" else
                "Listo: abono de ${Formato.moneda(valor)} · faltan ${Formato.moneda(queda)}", {
                RetrofitClient.api.crearAbono(request)
            }) { alGuardar() }
        } else {
            confirmarCambios(listOf(
                Cambio("Fecha", Formato.fechaLegible(existente.fecha), Formato.fechaLegible(fecha)),
                Cambio("Valor", Formato.moneda(existente.valor), Formato.moneda(valor)),
                Cambio("Nota", existente.nota.orEmpty(), nota)
            )) {
                guardarEnApi(dialogo, "Abono corregido: ${Formato.moneda(valor)}", {
                    RetrofitClient.api.actualizarAbono(id, request)
                }) { alGuardar() }
            }
        }
        false
    }
}
