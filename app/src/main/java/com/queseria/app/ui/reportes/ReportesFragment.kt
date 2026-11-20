package com.queseria.app.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.queseria.app.MainActivity
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.FragmentReportesBinding
import com.queseria.app.databinding.ItemBarraClienteBinding
import com.queseria.app.databinding.ItemDiaBinding
import com.queseria.app.models.Ganancias
import com.queseria.app.models.RendimientoSemanal
import com.queseria.app.models.ResumenSemana
import com.queseria.app.models.Venta
import com.queseria.app.ui.common.Grafico
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.home.RendimientoFragment
import com.queseria.app.ui.ventas.total
import com.queseria.app.util.Formato
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Pantalla de Reportes: ganancia de la semana, gráficos de las últimas 8 semanas
 * (ingresos vs. leche, ganancia, rendimiento), mejores clientes e inventario.
 */
class ReportesFragment : Fragment() {

    private var _binding: FragmentReportesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cabecera.tvTitulo.text = "Reportes"
        binding.cabecera.btnMenu.setOnClickListener { (activity as? MainActivity)?.mostrarMenu(it) }
        binding.cabecera.btnAccion.visibility = View.VISIBLE
        binding.cabecera.btnAccion.setOnClickListener { cargar() }
        listOf(binding.graficoIngresos, binding.graficoGanancia, binding.graficoRendimiento, binding.graficoMerma)
            .forEach { it.modoOscuro() }
        binding.swipeRefresh.setColorSchemeResources(R.color.gr_neon)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.gr_barra)
        binding.btnGanancias.setOnClickListener { findNavController().navigate(R.id.nav_ganancias) }
        binding.btnRendimiento.setOnClickListener { findNavController().navigate(R.id.nav_rendimiento) }
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        cargar()
    }

    private fun cargar() {
        cargarInventario()
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            // Las consultas se hacen en paralelo
            val ganancias = async { llamarApi { RetrofitClient.api.getGanancias() } }
            val semanas = async { llamarApi { RetrofitClient.api.getResumenSemanas(n = 8, fecha = Formato.hoyIso()) } }
            val rendimiento = async { llamarApi { RetrofitClient.api.getRendimientoSemanal() } }
            val merma = async { llamarApi { RetrofitClient.api.getMerma() } }
            val ventas = async {
                llamarApi { RetrofitClient.api.getVentas(limit = 500, desde = Formato.haceDias(30), hasta = Formato.hoyIso()) }
            }
            val rG = ganancias.await()
            val rS = semanas.await()
            val rR = rendimiento.await()
            val rV = ventas.await()
            val rM = merma.await()
            if (_binding == null) return@launch
            binding.swipeRefresh.isRefreshing = false

            (rG as? Resultado.Exito)?.data?.let { mostrarGanancias(it) }
            (rS as? Resultado.Exito)?.data?.let { mostrarGraficos(it) }
            (rR as? Resultado.Exito)?.data?.let { mostrarRendimiento(it) }
            (rV as? Resultado.Exito)?.let { mostrarVentasPorCliente(it.data.orEmpty()) }
            (rM as? Resultado.Exito)?.data?.let {
                binding.tvMermaPromedio.text = if (it.semanas.isEmpty()) "—" else "${Formato.numero(it.promedioPct, 1)} %"
            }

            val error = listOf(rG, rS, rR, rV, rM).filterIsInstance<Resultado.Error>().firstOrNull()
            binding.tvError.visibility = if (error == null) View.GONE else View.VISIBLE
            error?.let {
                binding.tvError.text = "Algunos datos no se pudieron cargar: ${it.mensaje}"
                manejarError(it)
            }
        }
    }

    private fun mostrarGanancias(g: Ganancias) {
        val s = g.semanaActual
        // Negativa en rojo, positiva en verde (nunca una pérdida en verde)
        binding.tvGananciaTitulo.text = if (s.completa) "Ganancia de esta semana" else "Ganancia de esta semana (hasta hoy)"
        binding.tvGanancia.text = Formato.moneda(s.ganancia)
        binding.tvGanancia.setTextColor(color(if (s.ganancia < 0) R.color.gr_negativo else R.color.gr_texto))
        val sinDatos = g.semanaAnterior.ganancia == 0.0 && g.variacion == 0.0
        binding.tvGananciaCambio.text = if (sinDatos) "Sin datos de la semana anterior" else
            "${if (g.variacion < 0) "▼" else "▲"} ${Formato.moneda(kotlin.math.abs(g.variacion))}" +
                (if (g.porcentaje != 0.0) " (${Formato.numero(kotlin.math.abs(g.porcentaje), 1)} %)" else "") +
                " vs. semana anterior"
        binding.tvGananciaCambio.setBackgroundResource(when {
            sinDatos -> R.drawable.bg_gr_pildora_verde
            g.variacion < 0 -> R.drawable.bg_gr_pildora_roja
            else -> R.drawable.bg_gr_pildora_verde
        })
        binding.tvSemanaInfo.text = "Semana ${s.semana} · " + (s.rango?.texto ?: "") +
            if (!s.completa) "\nLo vendido el viernes cuenta primero para la semana que cerró el jueves." else ""
        // La ilustración de la flecha sube; con pérdida se gira para que apunte hacia abajo
        binding.ivGanancia.scaleY = if (s.ganancia < 0) -1f else 1f
        binding.tvVentas.text = Formato.moneda(s.ingresos)
        binding.tvVentasDetalle.text = "${Formato.kg(s.kgVendidos)} vendidos"
        binding.tvLeche.text = Formato.moneda(s.costoLeche)
        binding.tvLecheDetalle.text = Formato.litros(s.litrosComprados) +
            if (s.costoLitro > 0) " × ${Formato.moneda(s.costoLitro)}" else " · sin precio del litro"
    }

    private fun mostrarGraficos(semanas: List<ResumenSemana>) {
        val etiquetas = semanas.map { "S${it.semana}" }
        val actual = semanas.lastIndex
        binding.graficoIngresos.mostrar(
            Grafico.Tipo.BARRAS,
            etiquetas,
            listOf(
                Grafico.Serie("Ventas", semanas.map { it.ingresos }, color(R.color.gr_neon)),
                Grafico.Serie("Leche comprada", semanas.map { it.costoLeche }, color(R.color.gr_oro))
            ),
            resaltar = actual,
            formatoValor = ::monedaCorta
        )
        binding.graficoGanancia.mostrar(
            Grafico.Tipo.BARRAS,
            etiquetas,
            listOf(Grafico.Serie("Ganancia", semanas.map { it.ganancia }, color(R.color.gr_positivo))),
            resaltar = actual,
            formatoValor = ::monedaCorta
        )
        binding.graficoMerma.mostrar(
            Grafico.Tipo.BARRAS,
            etiquetas,
            // Semanas sin completar (no ha terminado su viernes siguiente) quedan en 0: su merma todavía no existe
            listOf(Grafico.Serie("Merma", semanas.map { it.mermaPct ?: 0.0 }, color(R.color.gr_rojo))),
            resaltar = actual,
            formatoValor = { "${Formato.numero(it, 1)} %" }
        )
        mostrarDetalleMerma(semanas)
        binding.graficoRendimiento.mostrar(
            Grafico.Tipo.LINEA,
            etiquetas,
            listOf(Grafico.Serie("Libras por lata", semanas.map { it.librasPorLata }, color(R.color.gr_neon))),
            resaltar = actual,
            formatoValor = { Formato.numero(it, 1) }
        )
    }

    /**
     * Últimas 4 semanas con queso: kg y % de merma de las completas; las que no han terminado su viernes
     * siguiente dicen cuánto falta por vender (la merma no se inventa). "⚠" si el servidor pide revisar.
     */
    private fun mostrarDetalleMerma(semanas: List<ResumenSemana>) {
        val contenedor = binding.contenedorMerma
        contenedor.removeAllViews()
        semanas.filter { it.kgProducidos > 0 || (it.mermaKg ?: 0.0) != 0.0 }.takeLast(4).reversed().forEachIndexed { i, s ->
            val fila = ItemDiaBinding.inflate(layoutInflater, contenedor, false)
            val kg = s.mermaKg
            fila.tvDia.setTextColor(color(R.color.gr_texto))
            fila.tvEntregas.setTextColor(color(R.color.gr_texto_2))
            fila.tvDia.text = "Semana ${s.semana}" + if (s.revisar != null) " ⚠ revisar" else ""
            if (!s.completa || kg == null) {
                fila.tvEntregas.text = "Se completa al terminar el viernes · vendido ${Formato.kg(s.kgVendidos)} de ${Formato.kg(s.kgProducidos)}"
                fila.tvLitros.text = "—"
                fila.tvLitros.setTextColor(color(R.color.gr_texto_2))
            } else {
                fila.tvEntregas.text = "${Formato.numero(s.mermaPct ?: 0.0, 1)} % de ${Formato.kg(s.kgProducidos)}"
                fila.tvLitros.text = Formato.kg(kg)
                fila.tvLitros.setTextColor(color(if (kg < 0 || s.revisar != null) R.color.gr_negativo else R.color.gr_oro))
            }
            if (i % 2 == 0) {
                fila.root.setBackgroundResource(R.drawable.bg_chip)
                fila.root.backgroundTintList = android.content.res.ColorStateList.valueOf(0x14FFFFFF)
            }
            contenedor.addView(fila.root)
        }
    }

    private fun mostrarRendimiento(r: RendimientoSemanal) {
        binding.tvRendimiento.text = "${Formato.numero(r.promedioLibrasPorLata)} lb"
        binding.tvMejorSemana.text = r.mejorSemana?.let {
            "▲ Mejor: ${RendimientoFragment.textoSemana(it)} (${Formato.numero(it.librasPorLata)})"
        }
        binding.tvPeorSemana.text = r.peorSemana?.let {
            "▼ Peor: ${RendimientoFragment.textoSemana(it)} (${Formato.numero(it.librasPorLata)})"
        }
    }

    /** Agrupa las ventas por nombre de cliente y muestra los que más compraron, con barras proporcionales. */
    private fun mostrarVentasPorCliente(ventas: List<Venta>) {
        val contenedor = binding.contenedorClientes
        contenedor.removeAllViews()
        val grupos = ventas
            .groupBy { it.cliente?.trim()?.takeIf { n -> n.isNotEmpty() } ?: "Cliente ocasional" }
            .map { (cliente, lista) -> Triple(cliente, lista.size, lista.sumOf { it.total }) }
            .sortedByDescending { it.third }

        if (grupos.isEmpty()) {
            contenedor.addView(TextView(requireContext()).apply {
                text = "No hay ventas en los últimos 30 días."
                setTextColor(color(R.color.gr_texto_2))
                setPadding(0, 16, 0, 8)
            })
            return
        }
        val totalGeneral = ventas.sumOf { it.total }
        val mayor = grupos.first().third
        val colores = listOf(R.color.gr_neon, R.color.gr_positivo, R.color.gr_oro)
        grupos.take(6).forEachIndexed { i, (cliente, num, total) ->
            val fila = ItemBarraClienteBinding.inflate(layoutInflater, contenedor, false)
            fila.tvNombre.text = cliente
            fila.tvNombre.setTextColor(color(R.color.gr_texto))
            fila.tvTotal.setTextColor(color(R.color.gr_neon))
            fila.tvDetalle.setTextColor(color(R.color.gr_texto_2))
            fila.barra.trackColor = 0x22FFFFFF
            fila.tvTotal.text = Formato.moneda(total)
            fila.barra.setIndicatorColor(color(colores.getOrElse(i) { R.color.verde_principal }))
            fila.barra.progress = if (mayor > 0) (total * 100 / mayor).toInt().coerceAtLeast(2) else 0
            val porcentaje = if (totalGeneral > 0) " · ${Formato.numero(total * 100 / totalGeneral, 0)} % del total" else ""
            fila.tvDetalle.text = "$num ${if (num == 1) "compra" else "compras"}$porcentaje"
            contenedor.addView(fila.root)
        }
    }

    private fun cargarInventario() {
        conApi({ RetrofitClient.api.getProductos() }) { lista ->
            val productos = lista.orEmpty()
            val kg = productos.sumOf { it.stockKg.coerceAtLeast(0.0) }
            val valor = productos.sumOf { it.stockKg.coerceAtLeast(0.0) * it.precioPorKg }
            val bajos = productos.count { it.stockBajo }
            binding.tvInventarioValor.text = Formato.moneda(valor)
            binding.tvInventarioDetalle.text = "${Formato.kg(kg)} (${Formato.numero(Formato.kgALibras(kg))} lb) en " +
                "${productos.size} ${if (productos.size == 1) "producto" else "productos"}" +
                if (bajos > 0) " · $bajos con stock bajo" else ""
        }
    }

    /** $ 1,2 M · $ 350 mil · $ 900 (para que quepa encima de las barras). */
    private fun monedaCorta(valor: Double): String {
        val a = kotlin.math.abs(valor)
        val signo = if (valor < 0) "−" else ""
        return when {
            a >= 1_000_000 -> "$signo$ ${Formato.numero(a / 1_000_000, 1)} M"
            a >= 1_000 -> "$signo$ ${Formato.numero(a / 1_000, 0)} mil"
            else -> "$signo$ ${Formato.numero(a, 0)}"
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
