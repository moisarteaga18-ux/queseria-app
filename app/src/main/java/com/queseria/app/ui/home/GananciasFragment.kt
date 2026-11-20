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
        merma.text = textoMerma(s)
        ganancia.text = (if (s.completa) "Ganancia: " else "Ganancia hasta hoy: ") + Formato.moneda(s.ganancia)
        colorear(ganancia, s.ganancia)
    }

    companion object {
        /**
         * Merma de la semana tal como la calcula el servidor. Mientras no termine el viernes siguiente no
         * hay merma (no se inventa): se muestra lo producido, lo vendido y lo que falta por vender.
         */
        fun textoMerma(s: GananciaSemana): String {
            val kg = s.mermaKg
            if (!s.completa || kg == null) {
                val viernes = s.viernesSiguiente?.let { " del ${Formato.fechaLegible(it)}" } ?: ""
                return "Queso de la semana: ${Formato.kg(s.stockInicialKg + s.kgProducidos)} · vendido ${Formato.kg(s.kgVendidos)} · " +
                    "por vender ${Formato.kg(s.kgPorVender)}.\nLa merma y la ganancia de esta semana se completan al terminar el viernes$viernes."
            }
            val pct = Formato.numero(s.mermaPct ?: 0.0, 1)
            val texto = when {
                kg < 0 -> "Merma: ${Formato.kg(kg)} ($pct %)"
                kg == 0.0 -> "Merma: 0 kg"
                // Sin ventas ni precio registrado no se puede valorar (no es una pérdida de $ 0)
                s.precioVentaKg <= 0 -> "Merma: ${Formato.kg(kg)} · $pct % (sin valor: no hubo ventas ni precio del queso esa semana)"
                else -> "Pérdida por merma: −${Formato.moneda(s.valorMerma ?: 0.0)} " +
                    "(${Formato.kg(kg)} · $pct % × ${Formato.moneda(s.precioVentaKg)}/kg" +
                    (if (s.precioPromedioKg != null) ", precio promedio de venta)" else ", precio registrado)")
            }
            return texto + (s.revisar?.let { "\n⚠ Revisar: $it" } ?: "")
        }
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
