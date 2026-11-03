package com.queseria.app.ui.ganaderos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Ganadero
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.toast
import com.queseria.app.ui.liquidaciones.HistorialGanaderoFragment
import com.queseria.app.ui.liquidaciones.abrirFormularioDescuento
import androidx.navigation.fragment.findNavController

/**
 * Ganaderos que le venden la leche a la quesería: crear, editar, llamar y eliminar.
 */
class GanaderosFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val adapter = FilaAdapter<Ganadero>(
        aFila = { g -> Fila(titulo = g.nombre, subtitulo = g.telefono ?: "Sin teléfono", icono = R.drawable.ic_ganadero) },
        alTocar = { opciones(it) },
        alMantener = { eliminar(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Nuevo ganadero"
        binding.fabAgregar.setOnClickListener { abrirFormularioGanadero(null) { cargar() } }
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getGanaderos() }, binding.swipeRefresh) {
            val lista = it.orEmpty()
            adapter.actualizar(lista)
            binding.tvVacio.text = "Aún no tienes ganaderos.\nToca + Nuevo ganadero para agregar a quien te vende la leche."
            binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun opciones(g: Ganadero) {
        val acciones = listOfNotNull(
            "Ver liquidaciones", "Registrar adelanto o descuento", "Editar", g.telefono?.let { "Llamar" }, "Eliminar"
        )
        AlertDialog.Builder(requireContext())
            .setTitle(g.nombre)
            .setItems(acciones.toTypedArray()) { _, i ->
                when (acciones[i]) {
                    "Ver liquidaciones" -> findNavController().navigate(
                        R.id.nav_historial_ganadero, HistorialGanaderoFragment.args(g.id, g.nombre)
                    )
                    "Registrar adelanto o descuento" -> abrirFormularioDescuento(g.id, g.nombre)
                    "Editar" -> abrirFormularioGanadero(g) { cargar() }
                    "Llamar" -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${g.telefono}")))
                    "Eliminar" -> eliminar(g)
                }
            }
            .show()
    }

    private fun eliminar(g: Ganadero) {
        confirmar("Eliminar ganadero", "¿Eliminar a \"${g.nombre}\"? Si ya tiene leche o liquidaciones, se archiva y su historial se conserva.") {
            conApi({ RetrofitClient.api.eliminarGanadero(g.id) }) { r ->
                toast(if (r?.archivado == true) "Ganadero archivado (su historial se conserva)" else "Ganadero eliminado")
                cargar()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
