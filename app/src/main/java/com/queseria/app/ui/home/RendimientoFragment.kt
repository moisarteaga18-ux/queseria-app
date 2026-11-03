package com.queseria.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.CabeceraRendimientoBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.RendimientoSemanal
import com.queseria.app.models.SemanaRendimiento
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.manejarError
import com.queseria.app.util.Formato
import kotlinx.coroutines.launch

/**
 * Pantalla de Rendimiento: libras de queso por lata de leche, semana por semana,
 * con la mejor y la peor semana.
 */
class RendimientoFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraRendimientoBinding

    private var datos: RendimientoSemanal? = null

    private val adapter = FilaAdapter<SemanaRendimiento>(
        aFila = { s ->
            val esMejor = datos?.mejorSemana?.id == s.id && s.id != null
            val esPeor = datos?.peorSemana?.id == s.id && s.id != null
            Fila(
                titulo = textoSemana(s),
                subtitulo = "${Formato.litros(s.totalLitros)} · ${Formato.numero(s.totalLatas, 1)} latas",
                detalle = "${Formato.kg(s.totalKg)} · ${Formato.numero(s.totalLibras)} lb de queso",
                valor = "${Formato.numero(s.librasPorLata)} lb/lata",
                etiqueta = when {
                    esMejor -> "Mejor semana"
                    esPeor -> "Peor semana"
                    else -> null
                },
                colorEtiqueta = if (esMejor) R.color.verde_exito else R.color.ambar_advertencia,
                icono = R.drawable.ic_rendimiento,
                colorIcono = when {
                    esMejor -> R.color.verde_exito
                    esPeor -> R.color.ambar_advertencia
                    else -> R.color.verde_principal
                }
            )
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraRendimientoBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.fabAgregar.visibility = View.GONE
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        cargar()
    }

    private fun cargar() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.getRendimientoSemanal() }
            if (_binding == null) return@launch
            binding.swipeRefresh.isRefreshing = false
            when (r) {
                is Resultado.Exito -> mostrar(r.data ?: RendimientoSemanal())
                is Resultado.Error -> {
                    manejarError(r)
                    binding.tvVacio.text = "No se pudo cargar el rendimiento.\nDesliza hacia abajo para reintentar."
                    binding.tvVacio.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun mostrar(d: RendimientoSemanal) {
        datos = d
        adapter.actualizar(d.semanas)
        cabecera.tvPromedio.text = "${Formato.numero(d.promedioLibrasPorLata)} lb/lata"
        cabecera.tvMejor.text = d.mejorSemana?.let { "▲ Mejor: ${textoSemana(it)} (${Formato.numero(it.librasPorLata)} lb/lata)" }
        cabecera.tvPeor.text = d.peorSemana?.let { "▼ Peor: ${textoSemana(it)} (${Formato.numero(it.librasPorLata)} lb/lata)" }
        binding.tvVacio.text = "Aún no hay producciones registradas para calcular el rendimiento."
        binding.tvVacio.visibility = if (d.semanas.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun textoSemana(s: SemanaRendimiento): String =
            s.etiqueta ?: s.rango?.texto?.let { "Sem ${s.id?.semana ?: ""} · $it" } ?: "Semana ${s.id?.semana ?: "?"}"
    }
}
