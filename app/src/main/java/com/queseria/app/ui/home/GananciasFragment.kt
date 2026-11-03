package com.queseria.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.FragmentGananciasBinding
import com.queseria.app.models.GananciaSemana
import com.queseria.app.models.Ganancias
import com.queseria.app.ui.common.conApi
import com.queseria.app.util.Formato

/**
 * Pantalla de Ganancias: compara la semana actual con la anterior (ingresos, costo de la leche, ganancia).
 */
class GananciasFragment : Fragment() {

    private var _binding: FragmentGananciasBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGananciasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getGanancias() }, binding.swipeRefresh, alFallar = { r ->
            binding.tvError.text = "No se pudieron cargar las ganancias: ${r.mensaje}"
            binding.tvError.visibility = View.VISIBLE
        }) {
            binding.tvError.visibility = View.GONE
            it?.let { g -> mostrar(g) }
        }
    }

    private fun mostrar(g: Ganancias) {
        val signo = if (g.variacion >= 0) "+" else "−"
        binding.tvDiferencia.text = "$signo${Formato.moneda(kotlin.math.abs(g.variacion))}"
        colorear(binding.tvDiferencia, g.variacion)
        binding.tvPorcentaje.text = "${if (g.porcentaje >= 0) "▲" else "▼"} ${Formato.numero(kotlin.math.abs(g.porcentaje), 1)} %"
        colorear(binding.tvPorcentaje, g.porcentaje)
        // Sin ganancia la semana anterior el porcentaje no tiene sentido (el servidor manda 0)
        binding.tvPorcentaje.visibility = if (g.semanaAnterior.ganancia > 0) View.VISIBLE else View.GONE

        mostrarSemana(g.semanaActual, binding.tvRangoActual, binding.tvActualIngresos, binding.tvActualCostos, binding.tvActualMerma, binding.tvActualGanancia)
        mostrarSemana(g.semanaAnterior, binding.tvRangoAnterior, binding.tvAnteriorIngresos, binding.tvAnteriorCostos, binding.tvAnteriorMerma, binding.tvAnteriorGanancia)
    }

    private fun mostrarSemana(s: GananciaSemana, rango: TextView, ingresos: TextView, costos: TextView, merma: TextView, ganancia: TextView) {
        rango.text = "Semana ${s.semana} · " + (s.rango?.texto ?: rango(s.rango?.inicio, s.rango?.fin)) + " (viernes a jueves)"
        ingresos.text = "Ingresos: ${Formato.moneda(s.ingresos)}"
        costos.text = "Leche: ${Formato.litros(s.litrosComprados)} × ${Formato.moneda(s.costoLitro)} = ${Formato.moneda(s.costoLeche)}"
        val estimada = if (s.mermaEstimada) " (estimada)" else ""
        merma.text = when {
            s.mermaKg <= 0 -> "Merma: 0 kg"
            // Sin precio del queso esa semana no se puede valorar (no es una pérdida de $ 0)
            s.precioVentaKg <= 0 -> "Merma$estimada: ${Formato.kg(s.mermaKg)} · ${Formato.numero(s.mermaPct, 1)} % " +
                "(sin valor: falta el precio del queso de esa semana en Precios)"
            else -> "Pérdida por merma$estimada: −${Formato.moneda(s.valorMerma)} " +
                "(${Formato.kg(s.mermaKg)} · ${Formato.numero(s.mermaPct, 1)} % × ${Formato.moneda(s.precioVentaKg)}/kg)"
        }
        ganancia.text = "Ganancia: ${Formato.moneda(s.ganancia)}"
        colorear(ganancia, s.ganancia)
    }

    private fun rango(inicio: String?, fin: String?) =
        if (inicio == null || fin == null) "" else "${Formato.fechaLegible(inicio)} – ${Formato.fechaLegible(fin)}"

    private fun colorear(tv: TextView, valor: Double) {
        tv.setTextColor(requireContext().getColor(if (valor < 0) R.color.rojo_error else R.color.verde_exito))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
