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
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.ui.cobros.abrirAbonos
import com.queseria.app.ui.cobros.comoCobro
import com.queseria.app.databinding.CabeceraClienteBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Cliente
import com.queseria.app.models.Venta
import com.queseria.app.ui.common.dp
import com.queseria.app.ui.common.tablaVentas
import com.queseria.app.ui.common.totalesVentas
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.toast
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

    // Compras del cliente: misma tabla que Ventas (sin la columna Cliente). Tocar una fiada = sus abonos.
    private val adapter = tablaVentas(porSemana = false, conCliente = false) { v ->
        if (v.fiada == true) v.comoCobro()?.let { abrirAbonos(it) { recargar() } }
        else toast("Venta pagada. Para corregirla, ábrela en Ventas.")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraClienteBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.rvLista.setPadding(dp(8), 0, dp(8), dp(24))
        adapter.crearEncabezado(binding.contenedorFijo)
        binding.fabAgregar.visibility = View.GONE
        binding.swipeRefresh.setOnRefreshListener { cargarVentas() }

        cabecera.btnEditar.setOnClickListener {
            cliente?.let { c -> abrirFormularioCliente(c) { recargar() } }
        }
        cabecera.btnEliminar.setOnClickListener { eliminar() }
        cabecera.tarjetaDebe.setOnClickListener { findNavController().navigate(R.id.nav_cuentas_cobrar) }
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
        val debe = c.debe ?: 0.0
        cabecera.tvDebe.text = Formato.moneda(debe)
        cabecera.tvDebe.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
            if (debe > 0.5) R.color.ambar_advertencia else R.color.verde_principal))
        cabecera.tvDebeDetalle.text = if (debe > 0.5) "te debe · toca para ver las cuentas por cobrar" else "no te debe nada"
    }

    /** Después de un abono: lo que debe y sus compras. */
    private fun recargar() {
        conApi({ RetrofitClient.api.getCliente(clienteId) }) { c -> c?.let { mostrarCliente(it) } }
        cargarVentas()
    }

    private fun cargarVentas() {
        conApi({ RetrofitClient.api.getVentasCliente(clienteId) }, binding.swipeRefresh, alFallar = {
            binding.tvVacio.text = "No se pudo cargar el historial.\nDesliza hacia abajo para reintentar."
            binding.tvVacio.visibility = View.VISIBLE
        }) { lista ->
            val ventas = lista.orEmpty()
            adapter.mostrar(ventas.sortedByDescending { Formato.soloFecha(it.fecha) }, totalesVentas(ventas, conCliente = false),
                "Este cliente aún no tiene compras registradas.")
            cabecera.tvTotalComprado.text = Formato.moneda(ventas.sumOf { it.total })
            cabecera.tvNumCompras.text = ventas.size.toString()
            binding.tvVacio.text = "Este cliente aún no tiene compras registradas."
            binding.tvVacio.visibility = View.GONE
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
