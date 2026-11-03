package com.queseria.app.ui.clientes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraClienteBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Cliente
import com.queseria.app.models.Venta
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.toast
import com.queseria.app.ui.ventas.filaVenta
import com.queseria.app.ui.ventas.total
import com.queseria.app.util.Formato

/**
 * Detalle de un cliente: datos de contacto + sus compras.
 */
class ClienteDetalleFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraClienteBinding

    private var cliente: Cliente? = null
    private val clienteId by lazy { arguments?.getString(ARG_ID).orEmpty() }

    private val adapter = FilaAdapter<Venta>(aFila = { filaVenta(it, mostrarCliente = false) })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraClienteBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.fabAgregar.visibility = View.GONE
        binding.swipeRefresh.setOnRefreshListener { cargarVentas() }

        cabecera.btnEditar.setOnClickListener {
            cliente?.let { c -> abrirFormularioCliente(c) { mostrarCliente(it); cargarVentas() } }
        }
        cabecera.btnEliminar.setOnClickListener { eliminar() }
        cabecera.btnLlamar.setOnClickListener {
            cliente?.telefono?.let { tel -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))) }
        }

        conApi({ RetrofitClient.api.getCliente(clienteId) }, alFallar = { findNavController().navigateUp() }) { c ->
            if (c == null) {
                toast("Cliente no encontrado")
                findNavController().navigateUp()
                return@conApi
            }
            mostrarCliente(c)
            cargarVentas()
        }
    }

    private fun mostrarCliente(c: Cliente) {
        cliente = c
        cabecera.tvNombre.text = c.nombre
        cabecera.tvTipo.text = "Cliente ${c.tipo.lowercase()}"
        cabecera.tvTelefono.text = c.telefono
        cabecera.tvTelefono.visibility = if (c.telefono == null) View.GONE else View.VISIBLE
        cabecera.tvDireccion.text = c.direccion
        cabecera.tvDireccion.visibility = if (c.direccion == null) View.GONE else View.VISIBLE
        cabecera.tvNotas.text = c.notas
        cabecera.tvNotas.visibility = if (c.notas == null) View.GONE else View.VISIBLE
        cabecera.btnLlamar.isEnabled = c.telefono != null
    }

    private fun cargarVentas() {
        conApi({ RetrofitClient.api.getVentasCliente(clienteId) }, binding.swipeRefresh, alFallar = {
            binding.tvVacio.text = "No se pudo cargar el historial.\nDesliza hacia abajo para reintentar."
            binding.tvVacio.visibility = View.VISIBLE
        }) { lista ->
            val ventas = lista.orEmpty()
            adapter.actualizar(ventas)
            cabecera.tvTotalComprado.text = Formato.moneda(ventas.sumOf { it.total })
            cabecera.tvNumCompras.text = ventas.size.toString()
            binding.tvVacio.text = "Este cliente aún no tiene compras registradas."
            binding.tvVacio.visibility = if (ventas.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun eliminar() {
        val c = cliente ?: return
        confirmar("Eliminar cliente", "¿Eliminar a \"${c.nombre}\"? Sus ventas registradas no se borran.") {
            conApi({ RetrofitClient.api.eliminarCliente(c.id) }) {
                toast("Cliente eliminado")
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "clienteId"
    }
}
