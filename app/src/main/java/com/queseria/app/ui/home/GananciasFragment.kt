package com.queseria.app.ui.home

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.queseria.app.MainActivity
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.FragmentGananciasBinding
import com.queseria.app.databinding.VistaSemanaBinding
import com.queseria.app.models.GananciaSemana
import com.queseria.app.models.Ganancias
import com.queseria.app.ui.common.NavegadorSemana
import com.queseria.app.ui.common.conApi
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import kotlin.math.abs

/**
 * Ganancias (diseño oscuro): diferencia con la semana anterior, la semana elegida (botón calendario),
 * ingresos y leche, pérdida por merma, ganancia y la semana anterior. Las curvas pequeñas son las
 * últimas 8 semanas reales (resumen/semanas).
 */
class GananciasFragment : Fragment() {

    private var _binding: FragmentGananciasBinding? = null
    private val binding get() = _binding!!

    private var semana: Semana = Semana.actual()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGananciasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cabecera.tvTitulo.text = "Ganancias"
        binding.cabecera.lema.visibility = View.VISIBLE
        // La campana del diseño no se pone: la app no tiene notificaciones
        binding.cabecera.btnMenu.setOnClickListener { (activity as? MainActivity)?.mostrarMenu(it) }
        binding.btnSemana.setOnClickListener { elegirSemana() }
        binding.swipeRefresh.setColorSchemeResources(R.color.gr_neon)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.gr_barra)
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        cargar()
    }

    /** El botón calendario abre el selector de semana de siempre (flechas, viernes a jueves). */
    private fun elegirSemana() {
        val vista = VistaSemanaBinding.inflate(layoutInflater)
        var elegida = semana
        NavegadorSemana(vista, semana) { elegida = it }
        AlertDialog.Builder(requireContext())
            .setTitle("Elegir la semana")
            .setView(vista.root.apply { setPadding(24, 24, 24, 8) })
            .setPositiveButton("Ver") { _, _ -> semana = elegida; cargar() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun cargar() {
        val s = semana
        conApi({ RetrofitClient.api.getGanancias(semana = s.numero, anio = s.anio) }, binding.swipeRefresh, alFallar = { r ->
            binding.tvError.text = "No se pudieron cargar las ganancias: ${r.mensaje}"
            binding.tvError.visibility = View.VISIBLE
        }) {
            binding.tvError.visibility = View.GONE
            it?.let { g -> mostrar(g) }
        }
        // Curvas: 8 semanas hasta la elegida (si falla, las tarjetas se ven igual, sin curva)
        conApi({ RetrofitClient.api.getResumenSemanas(n = 8, fecha = Formato.hoyIso(), hasta = s.fin.toString()) }) { lista ->
            val semanas = lista.orEmpty()
            if (semanas.size < 2) return@conApi
            binding.curvaDiferencia.mostrar(semanas.map { it.ganancia }, color(R.color.gr_neon))
            binding.curvaIngresos.mostrar(semanas.map { it.ingresos }, color(R.color.gr_neon))
            binding.curvaLeche.mostrar(semanas.map { it.costoLeche }, color(R.color.gr_azul))
        }
    }

    private fun mostrar(g: Ganancias) {
        val s = g.semanaActual
        // 1. Diferencia: negativa en rojo con ▼, positiva en verde con ▲
        binding.tvDiferencia.text = (if (g.variacion < 0) "−" else "+") + Formato.moneda(abs(g.variacion))
        binding.tvDiferencia.setTextColor(colorSigno(g.variacion))
        val hayAnterior = g.semanaAnterior.ganancia > 0
        binding.tvPorcentaje.visibility = if (hayAnterior) View.VISIBLE else View.GONE
        if (hayAnterior) {
            binding.tvPorcentaje.text = "${if (g.porcentaje < 0) "▼" else "▲"} ${Formato.numero(abs(g.porcentaje), 1)} %"
            binding.tvPorcentaje.setBackgroundResource(if (g.porcentaje < 0) R.drawable.bg_gr_pildora_roja else R.drawable.bg_gr_pildora_verde)
        }

        // 2. Semana elegida
        binding.tvSemanaTitulo.text = if (semana.esActual) "Esta semana · Semana ${s.semana}" else "Semana ${s.semana}"
        binding.tvRangoActual.text = textoRango(s)

        // 3. Ingresos y leche
        binding.tvActualIngresos.text = Formato.moneda(s.ingresos)
        binding.tvActualIngresosDetalle.text = "${Formato.kg(s.kgVendidos)} vendidos"
        binding.tvActualLitros.text = Formato.litros(s.litrosComprados) +
            if (s.costoLitro > 0) " × ${Formato.moneda(s.costoLitro)}" else " · sin precio del litro"
        binding.tvActualCostos.text = Formato.moneda(s.costoLeche)

        // 4. Merma
        mostrarMerma(s)

        // 5. Ganancia
        binding.tvGananciaTitulo.text = if (s.completa) "Ganancia" else "Ganancia hasta hoy"
        binding.tvActualGanancia.text = Formato.moneda(s.ganancia)
        binding.tvActualGanancia.setTextColor(colorSigno(s.ganancia))
        binding.ivFlechaGanancia.rotation = if (s.ganancia < 0) 180f else 0f
        binding.ivFlechaGanancia.setColorFilter(colorSigno(s.ganancia))
        binding.ivFlechaGanancia.contentDescription = if (s.ganancia < 0) "Pérdida" else "Ganancia"

        // 6. Semana anterior
        val a = g.semanaAnterior
        binding.tvRangoAnterior.text = "Semana ${a.semana} · ${textoRango(a)}"
        binding.tvAnteriorIngresos.text = "Ingresos: ${Formato.moneda(a.ingresos)}"
        binding.tvAnteriorCostos.text = "Leche: ${Formato.litros(a.litrosComprados)} × ${Formato.moneda(a.costoLitro)} = ${Formato.moneda(a.costoLeche)}"
        binding.tvAnteriorMerma.text = textoMerma(a)
        binding.tvAnteriorGanancia.text = (if (a.completa) "Ganancia: " else "Ganancia hasta hoy: ") + Formato.moneda(a.ganancia)
        binding.tvAnteriorGanancia.setTextColor(colorSigno(a.ganancia))
    }

    /** Tarjeta roja: la pérdida por merma solo cuando la semana está completa; antes, lo que falta por vender. */
    private fun mostrarMerma(s: GananciaSemana) {
        val kg = s.mermaKg
        val tv = binding.tvActualMerma
        if (!s.completa || kg == null) {
            binding.tvMermaTitulo.text = "Merma de la semana"
            val viernes = s.viernesSiguiente?.let { " del ${Formato.fechaLegible(it)}" } ?: ""
            tv.text = "Queso: ${Formato.kg(s.stockInicialKg + s.kgProducidos)} · vendido ${Formato.kg(s.kgVendidos)} · " +
                "por vender ${Formato.kg(s.kgPorVender)}. Se completa al terminar el viernes$viernes."
            return
        }
        val pct = Formato.numero(s.mermaPct ?: 0.0, 1)
        binding.tvMermaTitulo.text = when {
            kg > 0 && s.precioVentaKg > 0 -> "Pérdida por merma: −${Formato.moneda(s.valorMerma ?: 0.0)}"
            else -> "Merma: ${Formato.kg(kg)}"
        }
        tv.text = when {
            kg == 0.0 -> "Sin merma esta semana"
            kg < 0 -> "${Formato.kg(kg)} ($pct %): se vendió más de lo registrado"
            s.precioVentaKg <= 0 -> "${Formato.kg(kg)} · $pct % (sin valor: no hubo ventas ni precio del queso)"
            else -> "${Formato.kg(kg)} · $pct % × ${Formato.moneda(s.precioVentaKg)}/kg" +
                if (s.precioPromedioKg != null) " (precio promedio)" else " (precio registrado)"
        } + (s.revisar?.let { "\n⚠ Revisar: $it" } ?: "")
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

    private fun textoRango(s: GananciaSemana): String {
        val r = s.rango ?: return ""
        return r.texto ?: if (r.inicio == null || r.fin == null) "" else "${Formato.fechaLegible(r.inicio)} – ${Formato.fechaLegible(r.fin)}"
    }

    private fun colorSigno(valor: Double) = color(if (valor < 0) R.color.gr_negativo else R.color.gr_positivo)

    private fun color(id: Int) = requireContext().getColor(id)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
