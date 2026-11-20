package com.queseria.app.ui.liquidaciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraTabsBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Descuento
import com.queseria.app.models.Liquidacion
import com.queseria.app.ui.common.Tabla
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.dp
import com.queseria.app.ui.common.tablaDescuentos
import com.queseria.app.ui.common.tablaLiquidaciones
import com.queseria.app.ui.common.totalesDescuentos
import com.queseria.app.ui.common.totalesLiquidaciones
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate

/**
 * Historial de un ganadero, en tablas: sus liquidaciones (para consultarlas o reenviarlas) y sus
 * adelantos y descuentos. El + registra un adelanto o descuento para ese ganadero.
 */
class HistorialGanaderoFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraTabsBinding

    private val ganaderoId by lazy { requireArguments().getString(ARG_GANADERO)!! }
    private val nombre by lazy { requireArguments().getString(ARG_NOMBRE).orEmpty() }

    private var liquidaciones: List<Liquidacion>? = null
    private var descuentos: List<Descuento>? = null
    private var enDescuentos = false

    private val tablaHistorial = tablaLiquidaciones(conGanadero = false) { l ->
        l.id?.let { findNavController().navigate(R.id.nav_liquidacion, LiquidacionDetalleFragment.argsExistente(it)) }
    }

    private val tablaAdelantos = tablaDescuentos({ nombre }) { d ->
        val semana = Semana(LocalDate.parse(Formato.soloFecha(d.fecha)))
        findNavController().navigate(R.id.nav_liquidacion, LiquidacionDetalleFragment.argsSemana(ganaderoId, semana.numero, semana.anio))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraTabsBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Liquidaciones"))
        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Adelantos y descuentos"))
        cabecera.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                enDescuentos = tab.position == 1
                mostrar()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.setPadding(dp(8), 0, dp(8), dp(96))
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Adelanto o descuento"
        binding.fabAgregar.setOnClickListener { abrirFormularioDescuento(ganaderoId, nombre) { cargar() } }
        mostrar()
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getLiquidaciones(ganaderoId = ganaderoId) }, binding.swipeRefresh) { lista ->
            liquidaciones = lista.orEmpty()
            mostrar()
        }
        conApi({ RetrofitClient.api.getDescuentos(ganaderoId = ganaderoId) }) { lista ->
            descuentos = lista.orEmpty()
            mostrar()
        }
    }

    private fun mostrar() {
        if (_binding == null) return
        val tabla: Tabla<*> = if (enDescuentos) tablaAdelantos.also { t ->
            val lista = descuentos.orEmpty()
            t.mostrar(lista, if (lista.isEmpty()) null else totalesDescuentos(lista),
                "$nombre no tiene adelantos ni descuentos.\nToca + Adelanto o descuento para registrar uno.")
        } else tablaHistorial.also { t ->
            val lista = liquidaciones.orEmpty()
            t.mostrar(lista, if (lista.isEmpty()) null else totalesLiquidaciones(lista),
                "$nombre aún no tiene liquidaciones.\nToca + Adelanto o descuento para registrar uno.")
        }
        if (binding.rvLista.adapter !== tabla) {
            binding.rvLista.adapter = tabla
            tabla.crearEncabezado(binding.contenedorFijo)
        }
        val lista = liquidaciones.orEmpty()
        val pagado = lista.filter { it.pagada }.sumOf { it.total }
        val pendiente = lista.filterNot { it.pagada }.sumOf { it.total }
        cabecera.tvResumen.text = "$nombre · pagado ${Formato.moneda(pagado)}" +
            if (pendiente > 0) " · por pagar ${Formato.moneda(pendiente)}" else ""
        cabecera.tvResumen.visibility = View.VISIBLE
        binding.tvVacio.visibility = View.GONE
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
