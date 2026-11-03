package com.queseria.app.ui.clientes

import androidx.fragment.app.Fragment
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.DialogClienteBinding
import com.queseria.app.models.Cliente
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario

/**
 * Formulario para crear ([cliente] = null) o editar un cliente.
 * Se usa desde la lista de clientes y desde su detalle.
 */
fun Fragment.abrirFormularioCliente(cliente: Cliente?, alGuardar: (Cliente) -> Unit = {}) {
    val f = DialogClienteBinding.inflate(layoutInflater)
    if (cliente != null) {
        f.etNombre.setText(cliente.nombre)
        f.etTelefono.setText(cliente.telefono)
        f.etDireccion.setText(cliente.direccion)
        f.etNotas.setText(cliente.notas)
        f.rbMayorista.isChecked = cliente.tipo == Cliente.MAYORISTA
    }

    mostrarFormulario(if (cliente == null) "Nuevo cliente" else "Editar cliente", f.root) { dialogo ->
        val nombre = f.etNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            f.etNombre.error = "Escribe el nombre"
            return@mostrarFormulario false
        }
        val datos = Cliente(
            id = cliente?.id ?: "",
            nombre = nombre,
            telefono = f.etTelefono.text.toString().trim().ifEmpty { null },
            direccion = f.etDireccion.text.toString().trim().ifEmpty { null },
            tipo = if (f.rbMayorista.isChecked) Cliente.MAYORISTA else Cliente.MINORISTA,
            notas = f.etNotas.text.toString().trim().ifEmpty { null }
        )
        if (cliente == null) {
            guardarEnApi(dialogo, "Cliente creado", { RetrofitClient.api.crearCliente(datos) }) { alGuardar(it ?: datos) }
        } else {
            guardarEnApi(dialogo, "Cliente actualizado", { RetrofitClient.api.actualizarCliente(cliente.id, datos) }) {
                alGuardar(it ?: datos)
            }
        }
        false
    }
}
