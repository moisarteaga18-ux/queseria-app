package com.queseria.app.ui.liquidaciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraTabsBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Liquidacion
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.conApi
import com.queseria.app.util.Formato

/**
 * Historial de liquidaciones de un ganadero, para consultarlas o reenviarlas.
 * El + registra un adelanto o descuento para ese ganadero.
 */
class HistorialGanaderoFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraTabsBinding

    private val ganaderoId by lazy { requireArguments().getString(ARG_GANADERO)!! }
    private val nombre by lazy { requireArguments().getString(ARG_NOMBRE).orEmpty() }

    private val adapter = FilaAdapter<Liquidacion>(
        aFila = { filaLiquidacion(it, mostrarGanadero = false) },
        alTocar = { l -> l.id?.let { findNavController().navigate(R.id.nav_liquidacion, LiquidacionDetalleFragment.argsExistente(it)) } }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraTabsBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cabecera.tabs.visibility = View.GONE
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Adelanto o descuento"
        binding.fabAgregar.setOnClickListener { abrirFormularioDescuento(ganaderoId, nombre) { cargar() } }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getLiquidaciones(ganaderoId = ganaderoId) }, binding.swipeRefresh) { lista ->
            val liquidaciones = lista.orEmpty()
            adapter.actualizar(liquidaciones)
            val pagado = liquidaciones.filter { it.pagada }.sumOf { it.total }
            val pendiente = liquidaciones.filterNot { it.pagada }.sumOf { it.total }
            cabecera.tvResumen.text = "$nombre · ${liquidaciones.size} ${if (liquidaciones.size == 1) "liquidación" else "liquidaciones"} · pagado ${Formato.moneda(pagado)}" +
                if (pendiente > 0) " · por pagar ${Formato.moneda(pendiente)}" else ""
            cabecera.tvResumen.visibility = View.VISIBLE
            binding.tvVacio.text = "$nombre aún no tiene liquidaciones.\nToca + Adelanto o descuento para registrar uno."
            binding.tvVacio.visibility = if (liquidaciones.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_GANADERO = "ganaderoId"
        const val ARG_NOMBRE = "nombre"

        fun args(ganaderoId: String, nombre: String) = bundleOf(ARG_GANADERO to ganaderoId, ARG_NOMBRE to nombre)
    }
}
