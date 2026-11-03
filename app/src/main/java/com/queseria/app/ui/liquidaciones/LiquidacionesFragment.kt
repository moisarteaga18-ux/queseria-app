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
import com.queseria.app.models.FormaPago
import com.queseria.app.models.Liquidacion
import com.queseria.app.models.SemanaLiquidacion
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.conApi
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana

/**
 * Liquidación a ganaderos, paso 1: elegir la semana (viernes a jueves).
 * - Pestaña "Semanas": las últimas semanas con lo que se debe en cada una. Tocar una abre sus
 *   ganaderos ([SemanaLiquidacionFragment]) → detalle → generar, enviar y marcar como pagado.
 * - Pestaña "Historial": todas las liquidaciones generadas.
 */
class LiquidacionesFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraTabsBinding

    private var semanas: List<SemanaLiquidacion>? = null
    private var historial: List<Liquidacion>? = null

    private val adapterSemanas = FilaAdapter<SemanaLiquidacion>(
        aFila = { filaSemana(it) },
        alTocar = { s -> findNavController().navigate(R.id.nav_liquidacion_semana, SemanaLiquidacionFragment.args(s.semana, s.anio)) }
    )

    private val adapterHistorial = FilaAdapter<Liquidacion>(
        aFila = { filaLiquidacion(it, mostrarGanadero = true) },
        alTocar = { l -> l.id?.let { findNavController().navigate(R.id.nav_liquidacion, LiquidacionDetalleFragment.argsExistente(it)) } }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraTabsBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.visibility = View.GONE

        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Semanas"))
        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Historial"))
        cabecera.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = mostrar()
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

    private fun enSemanas() = cabecera.tabs.selectedTabPosition != 1

    private fun cargar() {
        conApi({ RetrofitClient.api.getSemanasLiquidacion(n = 12) }, binding.swipeRefresh, alFallar = {
            if (semanas == null) semanas = emptyList()
            mostrar()
        }) {
            // La semana en curso siempre; las anteriores solo si tuvieron leche o liquidaciones
            semanas = it.orEmpty().filterIndexed { i, s -> i == 0 || s.ganaderos > 0 || s.litrosSinGanadero > 0 }
            adapterSemanas.actualizar(semanas.orEmpty())
            mostrar()
        }
        conApi({ RetrofitClient.api.getLiquidaciones() }) {
            historial = it.orEmpty()
            adapterHistorial.actualizar(historial.orEmpty())
            mostrar()
        }
    }

    private fun mostrar() {
        if (enSemanas()) {
            if (binding.rvLista.adapter !== adapterSemanas) binding.rvLista.adapter = adapterSemanas
            cabecera.tvResumen.text = "1. Elige la semana  ·  2. Toca un ganadero  ·  3. Genera la liquidación, envíala y márcala como pagada"
            cabecera.tvResumen.visibility = View.VISIBLE
            binding.tvVacio.text = "No se pudieron cargar las semanas.\nDesliza hacia abajo para reintentar."
            binding.tvVacio.visibility = if (semanas?.isEmpty() == true) View.VISIBLE else View.GONE
        } else {
            if (binding.rvLista.adapter !== adapterHistorial) binding.rvLista.adapter = adapterHistorial
            cabecera.tvResumen.visibility = View.GONE
            binding.tvVacio.text = "Aún no has generado liquidaciones.\nEn la pestaña Semanas, elige una semana y toca un ganadero."
            binding.tvVacio.visibility = if (historial?.isEmpty() == true) View.VISIBLE else View.GONE
        }
    }

    private fun filaSemana(s: SemanaLiquidacion): Fila {
        val semana = Semana.de(s.semana, s.anio)
        val sinLeche = s.ganaderos == 0
        val todoPagado = !sinLeche && s.pagadas == s.ganaderos
        val (etiqueta, color) = when {
            sinLeche -> "Sin leche" to R.color.gris_texto_secundario
            s.sinPrecio > 0 -> "Falta precio" to R.color.ambar_advertencia
            todoPagado -> "Pagada" to R.color.verde_exito
            !s.cerrada -> "En curso" to R.color.azul_info
            else -> "Por pagar" to R.color.ambar_advertencia
        }
        val ganaderos = if (s.ganaderos == 1) "1 ganadero" else "${s.ganaderos} ganaderos"
        return Fila(
            titulo = "Semana ${s.semana}" + if (!s.cerrada) " (en curso)" else "",
            subtitulo = "Vie ${dia(semana.inicio)} a jue ${dia(semana.fin)}\n" +
                if (sinLeche) "Nadie ha entregado leche" else "$ganaderos · ${Formato.litros(s.totalLitros)}",
            detalle = when {
                sinLeche -> null
                s.sinPrecio > 0 -> "Toca para definir el precio del litro"
                todoPagado -> "Todo pagado: ${Formato.moneda(s.totalPagado)}"
                s.pagadas > 0 -> "${s.pagadas} de ${s.ganaderos} pagados · total ${Formato.moneda(s.totalAPagar)}"
                else -> "Total ${Formato.moneda(s.totalAPagar)}"
            },
            valor = when {
                sinLeche || s.sinPrecio > 0 -> null
                todoPagado -> Formato.moneda(s.totalPagado)
                else -> Formato.moneda(s.totalPendiente)
            },
            colorValor = if (todoPagado) R.color.verde_exito else R.color.verde_oscuro,
            etiqueta = etiqueta,
            colorEtiqueta = color,
            icono = if (todoPagado) R.drawable.ic_check else R.drawable.ic_calendario,
            colorIcono = color
        )
    }

    /** "23 oct" */
    private fun dia(fecha: java.time.LocalDate) = Formato.fechaLegible(fecha.toString()).substringBeforeLast(" ")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Fila de historial de una liquidación (en Liquidaciones y en el historial de un ganadero). */
fun filaLiquidacion(l: Liquidacion, mostrarGanadero: Boolean): Fila {
    val semana = Semana(java.time.LocalDate.parse(Formato.soloFecha(l.fechaInicio)))
    return Fila(
        titulo = if (mostrarGanadero) l.nombreGanadero else "Semana ${l.semana} · ${semana.texto()}",
        subtitulo = if (mostrarGanadero) "Semana ${l.semana} · ${semana.texto()}" else Formato.litros(l.litros),
        detalle = LiquidacionPdf.numeroTexto(l.numero) +
            if (l.pagada) " · pagada el ${Formato.fechaLegible(l.fechaPago)} (${FormaPago.enFrase(l.formaPago)})" else " · por pagar",
        valor = Formato.moneda(l.total),
        colorValor = if (l.pagada) R.color.verde_exito else R.color.verde_oscuro,
        etiqueta = if (l.pagada) "Pagado" else "Pendiente",
        colorEtiqueta = if (l.pagada) R.color.verde_exito else R.color.ambar_advertencia,
        icono = R.drawable.ic_liquidacion,
        colorIcono = if (l.pagada) R.color.verde_exito else R.color.ambar_advertencia
    )
}
