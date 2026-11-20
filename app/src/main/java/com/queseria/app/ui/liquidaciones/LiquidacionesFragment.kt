package com.queseria.app.ui.liquidaciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.queseria.app.models.SemanaLiquidacion
import com.queseria.app.ui.common.Tabla
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.dp
import com.queseria.app.ui.common.tablaDescuentos
import com.queseria.app.ui.common.tablaLiquidaciones
import com.queseria.app.ui.common.tablaSemanasLiquidacion
import com.queseria.app.ui.common.totalesDescuentos
import com.queseria.app.ui.common.totalesLiquidaciones
import com.queseria.app.ui.common.totalesSemanasLiquidacion
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate

/**
 * Liquidación a ganaderos, paso 1: elegir la semana (viernes a jueves). Todo en tablas tipo hoja de cálculo.
 * - Pestaña "Semanas": las últimas semanas con lo que se debe en cada una. Tocar una abre sus
 *   ganaderos ([SemanaLiquidacionFragment]) → detalle → generar, enviar y marcar como pagado.
 * - Pestaña "Historial": todas las liquidaciones generadas (tocar = su detalle).
 * - Pestaña "Adelantos": todos los adelantos y descuentos (tocar = la liquidación donde se restan).
 */
class LiquidacionesFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraTabsBinding

    private var semanas: List<SemanaLiquidacion>? = null
    private var historial: List<Liquidacion>? = null
    private var descuentos: List<Descuento>? = null
    private var nombres: Map<String, String> = emptyMap()

    private val tablaSemanas = tablaSemanasLiquidacion { s ->
        findNavController().navigate(R.id.nav_liquidacion_semana, SemanaLiquidacionFragment.args(s.semana, s.anio))
    }

    private val tablaHistorial = tablaLiquidaciones(conGanadero = true) { l ->
        l.id?.let { findNavController().navigate(R.id.nav_liquidacion, LiquidacionDetalleFragment.argsExistente(it)) }
    }

    private val tablaAdelantos = tablaDescuentos({ nombres[it] ?: "Ganadero" }) { d -> abrirLiquidacionDe(d) }

    /** Pestaña que se está viendo (0 Semanas, 1 Historial, 2 Adelantos). */
    private var pestana = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraTabsBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.setPadding(dp(8), 0, dp(8), dp(24))
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.visibility = View.GONE

        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Semanas"))
        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Historial"))
        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Adelantos"))
        cabecera.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                pestana = tab.position
                mostrar()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        mostrar()
    }

    override fun onResume() {
        super.onResume()
        // Al volver de una semana o de un detalle (pagos, descuentos) se recarga
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getSemanasLiquidacion(n = 12) }, binding.swipeRefresh, alFallar = {
            if (semanas == null) semanas = emptyList()
            mostrar()
        }) {
            // La semana en curso siempre; las anteriores solo si tuvieron leche o liquidaciones
            semanas = it.orEmpty().filterIndexed { i, s -> i == 0 || s.ganaderos > 0 || s.litrosSinGanadero > 0 }
            mostrar()
        }
        conApi({ RetrofitClient.api.getLiquidaciones() }) {
            historial = it.orEmpty()
            mostrar()
        }
        conApi({ RetrofitClient.api.getGanaderos() }) { lista ->
            nombres = lista.orEmpty().associate { it.id to it.nombre }
            mostrar()
        }
        conApi({ RetrofitClient.api.getDescuentos() }) {
            descuentos = it.orEmpty()
            mostrar()
        }
    }

    private fun mostrar() {
        if (_binding == null) return
        val tabla: Tabla<*> = when (pestana) {
            1 -> tablaHistorial.also { t ->
                val lista = historial.orEmpty()
                t.mostrar(lista, if (lista.isEmpty()) null else totalesLiquidaciones(lista),
                    "Aún no has generado liquidaciones.\nEn la pestaña Semanas, elige una semana y toca un ganadero.")
            }
            2 -> tablaAdelantos.also { t ->
                val lista = descuentos.orEmpty()
                t.mostrar(lista, if (lista.isEmpty()) null else totalesDescuentos(lista),
                    "Aún no hay adelantos ni descuentos.\nSe registran desde Inicio → Adelanto o descuento.")
            }
            else -> tablaSemanas.also { t ->
                val lista = semanas.orEmpty()
                t.mostrar(lista, if (lista.isEmpty()) null else totalesSemanasLiquidacion(lista),
                    if (semanas == null) "Cargando…" else "No se pudieron cargar las semanas.\nDesliza hacia abajo para reintentar.")
            }
        }
        if (binding.rvLista.adapter !== tabla) {
            binding.rvLista.adapter = tabla
            tabla.crearEncabezado(binding.contenedorFijo)
        }
        cabecera.tvResumen.text = when (pestana) {
            0 -> "1. Elige la semana  ·  2. Toca un ganadero  ·  3. Genera la liquidación, envíala y márcala como pagada"
            1 -> "Toca una liquidación para verla, enviarla o marcarla como pagada."
            else -> "Toca un adelanto para ver la liquidación de la semana en que se descuenta."
        }
        cabecera.tvResumen.visibility = View.VISIBLE
        binding.tvVacio.visibility = View.GONE
    }

    /** Detalle de la liquidación del ganadero en la semana del descuento (allí se ve restado). */
    private fun abrirLiquidacionDe(d: Descuento) {
        val semana = Semana(LocalDate.parse(Formato.soloFecha(d.fecha)))
        findNavController().navigate(R.id.nav_liquidacion,
            LiquidacionDetalleFragment.argsSemana(d.ganaderoId, semana.numero, semana.anio))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
