package com.queseria.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.CabeceraPreciosBinding
import com.queseria.app.databinding.DialogPrecioBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Precio
import com.queseria.app.models.PrecioSemanaRequest
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.elegirFecha
import com.queseria.app.ui.common.guardarConConfirmacion
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Pantalla de Precios: por cada semana (viernes a jueves), el precio de venta del queso por kg y
 * el precio del litro de leche. El litro se guarda en un solo lugar: es el mismo que se usa en las
 * liquidaciones a los ganaderos, en Ganancias y en Reportes.
 * Tocar una semana = corregir sus precios.
 */
class PreciosFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraPreciosBinding

    private var lista: List<Precio> = emptyList()

    private val adapter = FilaAdapter<Precio>(
        aFila = { p ->
            val semana = Semana.de(p.semana, p.anio)
            Fila(
                titulo = "Semana ${p.semana} · ${p.anio}",
                subtitulo = "${Formato.fechaLegible(p.fechaInicio)} – ${Formato.fechaLegible(p.fechaFin)}",
                detalle = "Mayorista: " + (p.precioMayoristaPorKg?.let { "${Formato.moneda(it)} / kg" } ?: "sin definir") +
                    " · Litro: " + (p.precioLitro?.let { Formato.moneda(it) } ?: "sin definir"),
                valor = p.precioPorKg?.let { "${Formato.moneda(it)} / kg" } ?: "Minorista: —",
                etiqueta = if (semana.esActual) "Esta semana" else null,
                colorEtiqueta = R.color.verde_exito,
                icono = R.drawable.ic_precios
            )
        },
        alTocar = { abrirFormulario(Semana.de(it.semana, it.anio), it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraPreciosBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Precio de la semana"
        binding.fabAgregar.setOnClickListener {
            val actual = Semana.actual()
            abrirFormulario(actual, lista.firstOrNull { it.semana == actual.numero && it.anio == actual.anio })
        }
        cargar()
    }

    private fun cargar() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.getPrecios() }
            if (_binding == null) return@launch
            binding.swipeRefresh.isRefreshing = false
            when (r) {
                is Resultado.Exito -> {
                    lista = r.data.orEmpty()
                    val actual = Semana.actual()
                    mostrarActual(lista.firstOrNull { it.semana == actual.numero && it.anio == actual.anio })
                    adapter.actualizar(lista)
                    binding.tvVacio.text = "Aún no tienes precios.\nToca + Precio de la semana para definir los de esta semana."
                    binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
                is Resultado.Error -> {
                    manejarError(r)
                    binding.tvVacio.text = "No se pudieron cargar los precios."
                    binding.tvVacio.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun mostrarActual(p: Precio?) {
        val semana = Semana.actual()
        cabecera.tvSemana.text = "Semana ${semana.numero}: ${semana.textoConDias()}" +
            when {
                p == null -> "\nNo has definido los precios de esta semana. Toca + Precio de la semana."
                p.precioPorKg == null -> "\nFalta el precio minorista del queso: toca + Precio de la semana."
                p.precioMayoristaPorKg == null -> "\nFalta el precio mayorista del queso: toca + Precio de la semana."
                p.precioLitro == null -> "\nFalta el precio del litro de leche: toca + Precio de la semana."
                else -> ""
            }
        cabecera.tvPrecioKg.text = p?.precioPorKg?.let { Formato.moneda(it) } ?: "—"
        cabecera.tvPrecioLb.text = textoLibra(p?.precioPorKg, p?.precioPorLibra)
        cabecera.tvMayoristaKg.text = p?.precioMayoristaPorKg?.let { Formato.moneda(it) } ?: "—"
        cabecera.tvMayoristaLb.text = textoLibra(p?.precioMayoristaPorKg, p?.precioMayoristaPorLibra)
        cabecera.tvCostoLeche.text = p?.precioLitro?.let { Formato.moneda(it) } ?: "—"
    }

    /** "por kg · $ X la lb" debajo de un precio del queso. */
    private fun textoLibra(porKg: Double?, porLibra: Double?) =
        porKg?.let { "por kg · ${Formato.moneda(porLibra ?: (it / Formato.LIBRAS_POR_KG))} la lb" } ?: "por kg"

    /**
     * Precios de una semana. [existente] = los que ya tiene (para corregirlos); si no tiene, se
     * proponen los de la semana más reciente.
     */
    private fun abrirFormulario(inicial: Semana, existente: Precio?) {
        val f = DialogPrecioBinding.inflate(layoutInflater)
        var semana = inicial

        fun precioDe(s: Semana) = lista.firstOrNull { it.semana == s.numero && it.anio == s.anio }
        fun llenar(p: Precio?) {
            // Sin precios en esa semana: se proponen los de la más reciente
            val base = p ?: lista.firstOrNull()
            f.etPrecioKg.setText((p?.precioPorKg ?: base?.precioPorKg)?.let { Formato.campo(it) }.orEmpty())
            f.etMayoristaKg.setText((p?.precioMayoristaPorKg ?: base?.precioMayoristaPorKg)?.let { Formato.campo(it) }.orEmpty())
            f.etCostoLeche.setText((p?.precioLitro ?: base?.precioLitro)?.let { Formato.campo(it) }.orEmpty())
        }
        fun mostrarSemana() {
            f.tvSemana.text = "Semana ${semana.numero}: ${semana.textoConDias()}"
        }
        mostrarSemana()
        // Se elige cualquier día y se toma la semana viernes–jueves que lo contiene
        f.tvSemana.setOnClickListener {
            elegirFecha(semana.inicio.toString()) {
                semana = Semana.de(LocalDate.parse(it))
                mostrarSemana()
                llenar(precioDe(semana))
            }
        }
        // Equivalente por libra debajo de cada precio del queso
        listOf(f.etPrecioKg to f.tvPrecioLb, f.etMayoristaKg to f.tvMayoristaLb).forEach { (campo, libra) ->
            campo.doAfterTextChanged {
                val kg = Formato.leerNumero(it) ?: 0.0
                libra.text = if (kg > 0) "= ${Formato.moneda(kg / Formato.LIBRAS_POR_KG)} por libra" else ""
            }
        }
        llenar(existente)

        val titulo = if (existente == null) "Precios de la semana" else "Corregir precios"
        mostrarFormulario(titulo, f.root) { dialogo ->
            /** Lee un precio opcional: vacío = null; inválido = marca el error y devuelve -1. */
            fun leer(campo: android.widget.EditText): Double? {
                val texto = campo.text.toString().trim()
                if (texto.isEmpty()) return null
                val valor = Formato.leerNumero(texto)
                if (valor == null || valor <= 0) {
                    campo.error = "Precio inválido"
                    return -1.0
                }
                return valor
            }
            val precioKg = leer(f.etPrecioKg)
            val mayorista = leer(f.etMayoristaKg)
            val litro = leer(f.etCostoLeche)
            if (listOf(precioKg, mayorista, litro).any { it == -1.0 }) return@mostrarFormulario false
            if (precioKg == null && mayorista == null && litro == null) {
                f.etPrecioKg.error = "Ingresa al menos un precio"
                return@mostrarFormulario false
            }
            val request = PrecioSemanaRequest(
                semana.numero, semana.anio,
                precioPorKg = precioKg, precioMayoristaPorKg = mayorista, precioLitro = litro
            )
            guardarConConfirmacion(dialogo, "Precios de la semana ${semana.numero} guardados", { confirmar ->
                RetrofitClient.api.guardarPrecioSemana(request.copy(confirmar = confirmar.takeIf { it }))
            }) { cargar() }
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
