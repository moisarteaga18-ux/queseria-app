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
import com.queseria.app.models.RendimientoSemanal
import com.queseria.app.models.ResumenSemana
import com.queseria.app.models.Venta
import com.queseria.app.ui.common.Grafico
import com.queseria.app.ui.common.NavegadorSemana
import com.queseria.app.ui.common.filasComparar
import com.queseria.app.ui.common.semanaDe
import com.queseria.app.ui.common.tablaComparar
import com.queseria.app.ui.common.tablaReporteSemanas
import com.queseria.app.ui.common.totalesReporteSemanas
import com.queseria.app.util.Semana
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.home.RendimientoFragment
import com.queseria.app.ui.merma.registrarMerma
import com.queseria.app.ui.ventas.total
import com.queseria.app.util.Formato
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Pantalla de Reportes, por semana (viernes a jueves): selector de semana, tabla "semana elegida vs. la anterior",
 * tabla de las 8 semanas hasta la elegida (con totales) y debajo los gráficos de esas 8 semanas, rendimiento,
 * merma, mejores clientes e inventario. Las tablas tienen el estilo de Leche y Ventas (ui/common/Tabla.kt).
 */
class ReportesFragment : Fragment() {

    private var _binding: FragmentReportesBinding? = null
    private val binding get() = _binding!!
    private lateinit var navegador: NavegadorSemana

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
        // Merma manual de la semana elegida: registrar o corregir
        binding.btnMerma.setOnClickListener {
            val s = navegador.semana
            registrarMerma(s.numero, s.anio) { cargarSemanas(); cargar() }
        }
        binding.btnRendimiento.setOnClickListener { findNavController().navigate(R.id.nav_rendimiento) }
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        selectorOscuro()
        navegador = NavegadorSemana(binding.selectorSemana, semanaElegida ?: Semana.actual()) {
            semanaElegida = it
            cargarSemanas()
        }
        cargar()
    }

    /** El selector de semana con los colores de esta pantalla (oscura con neón). */
    private fun selectorOscuro() {
        val sel = binding.selectorSemana
        sel.root.setBackgroundResource(R.drawable.bg_gr_tarjeta)
        sel.root.elevation = 0f
        sel.tvSemana.setTextColor(color(R.color.gr_texto))
        sel.tvSemanaDias.setTextColor(color(R.color.gr_texto_2))
        listOf(sel.btnSemanaAnterior, sel.btnSemanaSiguiente).forEach {
            it.setBackgroundResource(R.drawable.bg_gr_boton_redondo)
            it.imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.gr_neon))
        }
    }

    private fun cargar() {
        cargarInventario()
        cargarSemanas()
        viewLifecycleOwner.lifecycleScope.launch {
            val rendimiento = async { llamarApi { RetrofitClient.api.getRendimientoSemanal() } }
            val merma = async { llamarApi { RetrofitClient.api.getMerma() } }
            val ventas = async {
                llamarApi { RetrofitClient.api.getVentas(limit = 500, desde = Formato.haceDias(30), hasta = Formato.hoyIso()) }
            }
            val rR = rendimiento.await()
            val rV = ventas.await()
            val rM = merma.await()
            if (_binding == null) return@launch
            (rR as? Resultado.Exito)?.data?.let { mostrarRendimiento(it) }
            (rV as? Resultado.Exito)?.let { mostrarVentasPorCliente(it.data.orEmpty()) }
            (rM as? Resultado.Exito)?.data?.let {
                binding.tvMermaPromedio.text = if (it.semanas.isEmpty()) "—" else "${Formato.numero(it.promedioPct, 1)} %"
            }
            mostrarError(listOf(rR, rV, rM).filterIsInstance<Resultado.Error>().firstOrNull())
        }
    }

    /** Tablas y gráficos: las 8 semanas que terminan en la elegida (una sola consulta). */
    private fun cargarSemanas() {
        val s = navegador.semana
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.getResumenSemanas(n = 8, fecha = Formato.hoyIso(), hasta = s.fin.toString()) }
            if (_binding == null || navegador.semana != s) return@launch
            binding.swipeRefresh.isRefreshing = false
            when (r) {
                is Resultado.Exito -> {
                    val lista = r.data.orEmpty()
                    mostrarTablas(s, lista)
                    mostrarGraficos(lista)
                }
                is Resultado.Error -> mostrarError(r)
            }
        }
    }

    private fun mostrarError(error: Resultado.Error?) {
        if (_binding == null) return
        binding.tvError.visibility = if (error == null) View.GONE else View.VISIBLE
        error?.let {
            binding.tvError.text = "Algunos datos no se pudieron cargar: ${it.mensaje}"
            manejarError(it)
        }
    }

    private fun mostrarTablas(s: Semana, lista: List<ResumenSemana>) {
        val elegida = lista.lastOrNull { it.semana == s.numero && it.anio == s.anio }
        val anterior = s.anterior()
        val previa = lista.lastOrNull { it.semana == anterior.numero && it.anio == anterior.anio }
        binding.tvCompararTitulo.text = if (s.esActual) "Esta semana vs. la anterior" else "Semana ${s.numero} vs. semana ${anterior.numero}"
        binding.tvCompararAviso.text = when {
            elegida == null -> ""
            !elegida.completa -> "Va hasta hoy. Lo vendido el viernes cuenta primero para la semana que cerró el jueves."
            else -> s.texto()
        } + "\nDesliza la tabla a la izquierda para ver la diferencia."
        // Botón de la merma: solo si la semana ya cerró y tuvo queso
        binding.btnMerma.visibility = if (elegida != null && (elegida.mermaPendiente == true || elegida.mermaKg != null)) View.VISIBLE else View.GONE
        binding.btnMerma.text = when {
            elegida?.mermaKg != null -> "Merma: ${Formato.kg(elegida.mermaKg)} · tocar para corregir"
            else -> "Merma pendiente por registrar · Registrar"
        }
        val comparar = tablaComparar(s, anterior)
        if (elegida == null) comparar.llenarEn(binding.tablaComparar, emptyList(), null, "Sin datos de esta semana")
        else comparar.llenarEn(binding.tablaComparar, filasComparar(elegida, previa), null, "")

        // Sin las semanas vacías anteriores al primer registro (el gráfico sí las muestra, en 0)
        val conDatos = lista.dropWhile { it.litrosLeche == 0.0 && it.kgProducidos == 0.0 && it.ingresos == 0.0 }
        val semanas = tablaReporteSemanas { fila -> navegador.ir(semanaDe(fila)) }
        semanas.llenarEn(binding.tablaSemanas, conDatos, if (conDatos.isEmpty()) null else totalesReporteSemanas(conDatos),
            "Todavía no hay registros")
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
            // Semanas sin merma (pendiente) quedan en 0: no se inventa ningún número
            listOf(Grafico.Serie("Merma", semanas.map { it.mermaPct ?: 0.0 }, color(R.color.gr_rojo))),
            resaltar = actual,
            formatoValor = { "${Formato.numero(it, 1)} %" }
        )
        binding.graficoRendimiento.mostrar(
            Grafico.Tipo.LINEA,
            etiquetas,
            listOf(Grafico.Serie("Libras por lata", semanas.map { it.librasPorLata }, color(R.color.gr_neon))),
            resaltar = actual,
            formatoValor = { Formato.numero(it, 1) }
        )
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

    companion object {
        /** Semana que se estaba viendo (se conserva al volver del detalle de la ganancia). */
        private var semanaElegida: Semana? = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
