package com.queseria.app.ui.clientes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraBuscarBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Cliente
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.conApi

/**
 * Lista de clientes con buscador.
 * Tocar un cliente abre su detalle con el historial de compras.
 */
class ClientesFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraBuscarBinding

    private var todos: List<Cliente> = emptyList()

    private val adapter = FilaAdapter<Cliente>(
        aFila = { c ->
            Fila(
                titulo = c.nombre,
                subtitulo = listOfNotNull(c.telefono, c.direccion).joinToString(" · ").ifEmpty { null },
                etiqueta = c.tipo,
                colorEtiqueta = if (c.tipo == Cliente.MAYORISTA) R.color.azul_info else R.color.verde_principal,
                icono = R.drawable.ic_cliente,
                colorIcono = if (c.tipo == Cliente.MAYORISTA) R.color.azul_info else R.color.verde_principal
            )
        },
        alTocar = { c ->
            findNavController().navigate(R.id.nav_cliente_detalle, bundleOf(ClienteDetalleFragment.ARG_ID to c.id))
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraBuscarBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Nuevo cliente"
        binding.fabAgregar.setOnClickListener { abrirFormularioCliente(null) { cargar() } }
        cabecera.etBuscar.doAfterTextChanged { filtrar() }
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getClientes() }, binding.swipeRefresh) {
            todos = it.orEmpty()
            filtrar()
        }
    }

    private fun filtrar() {
        val texto = cabecera.etBuscar.text.toString().trim()
        val lista = if (texto.isEmpty()) todos else todos.filter {
            it.nombre.contains(texto, ignoreCase = true) || (it.telefono?.contains(texto) == true)
        }
        adapter.actualizar(lista)
        binding.tvVacio.text = if (todos.isEmpty()) "Aún no tienes clientes.\nToca + Nuevo cliente para agregar uno."
        else "Ningún cliente coincide con \"$texto\""
        binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
