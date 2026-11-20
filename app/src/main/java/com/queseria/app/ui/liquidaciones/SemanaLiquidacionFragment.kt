package com.queseria.app.ui.liquidaciones

import com.queseria.app.offline.necesitaInternet
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraLiquidacionesBinding
import com.queseria.app.databinding.DialogPrecioLecheBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.FormaPago
import com.queseria.app.models.Liquidacion
import com.queseria.app.models.LiquidacionGanadero
import com.queseria.app.models.LiquidacionesSemana
import com.queseria.app.models.PrecioLecheRequest
import com.queseria.app.models.SemanaRequest
import com.queseria.app.ui.common.dp
import com.queseria.app.ui.common.tablaGanaderosSemana
import com.queseria.app.ui.common.totalesGanaderosSemana
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.guardarConConfirmacion
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.toast
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate

/**
 * Paso 2 de la liquidación: los ganaderos de UNA semana (viernes a jueves) con lo que se le debe
 * a cada uno, el precio del litro y los totales. Al pasar el jueves de corte el backend genera
 * las liquidaciones que falten. Tocar un ganadero abre su detalle (paso 3).
 */
class SemanaLiquidacionFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraLiquidacionesBinding

    private lateinit var semana: Semana
    private var datos: LiquidacionesSemana? = null

    // Ganaderos de la semana en tabla; tocar uno abre su detalle
    private val adapter = tablaGanaderosSemana { abrirDetalle(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraLiquidacionesBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        semana = Semana.de(args.getInt(ARG_SEMANA), args.getInt(ARG_ANIO))
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Semana ${semana.numero}"

        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.rvLista.setPadding(dp(8), 0, dp(8), dp(24))
        adapter.crearEncabezado(binding.contenedorFijo)
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.visibility = View.GONE
        // La semana se elige en el paso anterior
        cabecera.tabs.visibility = View.GONE
        cabecera.btnSemanaAnterior.visibility = View.INVISIBLE
        cabecera.btnSemanaSiguiente.visibility = View.INVISIBLE
        cabecera.filaPrecio.setOnClickListener { abrirPrecio() }
        cabecera.btnGenerar.setOnClickListener { generarSemana() }
        cabecera.tvAviso.setOnClickListener {
            // Leche sin ganadero: se corrige en Leche recibida
            if ((datos?.litrosSinGanadero ?: 0.0) > 0) findNavController().navigate(R.id.nav_leche)
        }
        mostrarEncabezado()
    }

    override fun onResume() {
        super.onResume()
        // Al volver del detalle (pagos, descuentos) se recarga
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getLiquidacionesSemana(semana.numero, semana.anio) }, binding.swipeRefresh, alFallar = {
            binding.tvVacio.text = "No se pudo cargar la semana.\nDesliza hacia abajo para reintentar."
            binding.tvVacio.visibility = View.VISIBLE
        }) {
            datos = it
            val lista = it?.ganaderos.orEmpty()
            adapter.mostrar(lista, if (lista.isEmpty()) null else totalesGanaderosSemana(lista),
                "Ningún ganadero entregó leche esta semana.\nRegistra la leche desde el menú: Registrar leche.")
            mostrarEncabezado()
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)

    private fun mostrarEncabezado() {
        cabecera.tvSemana.text = "Semana ${semana.numero}" + if (semana.esActual) " · en curso" else ""
        cabecera.tvRango.text = "Viernes ${semana.texto().replace(" – ", " a jueves ")}"

        val d = datos
        val cerrada = d?.cerrada ?: semana.fin.isBefore(LocalDate.now())
        val (textoEstado, colorEstado) = if (cerrada) "Cerrada el jueves" to R.color.verde_exito
        else "Abierta · corte el jueves ${Formato.fechaLegible(semana.fin.toString()).substringBeforeLast(" ")}" to R.color.ambar_advertencia
        cabecera.tvEstadoSemana.text = textoEstado
        cabecera.tvEstadoSemana.setTextColor(color(colorEstado))
        cabecera.tvEstadoSemana.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color(colorEstado), 0x22))

        val sinPrecio = d != null && d.precioLitro == null
        cabecera.tvPrecioLitro.text = d?.precioLitro?.let { "${Formato.moneda(it)} / L" } ?: if (d == null) "—" else "Sin definir"
        cabecera.tvPrecioLitro.setTextColor(color(if (sinPrecio) R.color.ambar_advertencia else R.color.verde_oscuro))
        cabecera.btnCambiarPrecio.text = if (sinPrecio) "Definir precio" else "Cambiar"
        cabecera.tvTotalLitros.text = Formato.litros(d?.totalLitros ?: 0.0)
        cabecera.tvTotalPagar.text = Formato.moneda(d?.totalAPagar ?: 0.0)
        cabecera.tvPendiente.text = Formato.moneda(d?.totalPendiente ?: 0.0)

        val avisos = listOfNotNull(
            if (sinPrecio && d!!.ganaderos.isNotEmpty()) "Paso 1: toca «Definir precio» y escribe cuánto pagas el litro esta semana." else null,
            if ((d?.litrosSinGanadero ?: 0.0) > 0) "${Formato.litros(d!!.litrosSinGanadero)} de esta semana no tienen ganadero y no se liquidan. Toca aquí para asignarlos." else null
        )
        cabecera.tvAviso.text = avisos.joinToString("\n")
        cabecera.tvAviso.visibility = if (avisos.isEmpty()) View.GONE else View.VISIBLE

        val sinLiquidar = d?.sinLiquidar ?: 0
        cabecera.btnGenerar.visibility = if (d?.precioLitro != null && sinLiquidar > 0) View.VISIBLE else View.GONE
        cabecera.btnGenerar.text = if (sinLiquidar == 1) "Generar 1 liquidación" else "Generar las $sinLiquidar liquidaciones"

        cabecera.tvAyuda.visibility = if (d?.ganaderos?.isNotEmpty() == true) View.VISIBLE else View.GONE
        cabecera.tvAyuda.text = "Toca un ganadero para ver su detalle, generar la liquidación, enviarla por WhatsApp y marcarla como pagada."
        binding.tvVacio.text = "Ningún ganadero entregó leche esta semana.\nRegistra la leche desde el menú: Registrar leche."
        binding.tvVacio.visibility = View.GONE
    }

    private fun abrirDetalle(g: LiquidacionGanadero) {
        val args = g.liquidacion?.let { LiquidacionDetalleFragment.argsExistente(it.id) }
            ?: LiquidacionDetalleFragment.argsSemana(g.ganaderoId, semana.numero, semana.anio)
        findNavController().navigate(R.id.nav_liquidacion, args)
    }

    private fun abrirPrecio() {
        abrirPrecioLeche(semana, datos?.precioLitro) { cargar() }
    }

    private fun generarSemana() {
        if (necesitaInternet("Generar las liquidaciones", "el número consecutivo lo da el servidor, así no se repite")) return
        conApi({ RetrofitClient.api.generarLiquidacionesSemana(SemanaRequest(semana.numero, semana.anio)) }) { lista ->
            val n = lista.orEmpty().size
            toast(if (n == 1) "Listo: 1 liquidación generada" else "Listo: $n liquidaciones generadas")
            cargar()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SEMANA = "semana"
        const val ARG_ANIO = "anio"

        fun args(semana: Int, anio: Int) = bundleOf(ARG_SEMANA to semana, ARG_ANIO to anio)
    }
}

/**
 * Formulario del precio del litro de una semana (lo mismo para todos los ganaderos).
 * Se usa en la semana y en el detalle de una liquidación.
 */
fun Fragment.abrirPrecioLeche(semana: Semana, precioActual: Double?, alGuardar: () -> Unit) {
    val f = DialogPrecioLecheBinding.inflate(layoutInflater)
    f.tvSemana.text = "Semana ${semana.numero}: ${semana.texto()} (viernes a jueves) · aplica a todos los ganaderos"
    precioActual?.takeIf { it > 0 }?.let { f.etPrecio.setText(Math.round(it).toString()) }
    mostrarFormulario("Precio del litro", f.root, foco = f.etPrecio) { dialogo ->
        val (precio, error) = Formato.validarPositivo(f.etPrecio.text, "Escribe cuánto pagas por litro", "El precio del litro")
        if (precio == null) {
            f.etPrecio.error = error
            return@mostrarFormulario false
        }
        // Si la semana ya tiene liquidaciones pagadas, el backend pide confirmación
        guardarConConfirmacion(dialogo, "Precio guardado: ${Formato.moneda(precio)} por litro", { confirmar ->
            RetrofitClient.api.fijarPrecioLeche(PrecioLecheRequest(semana.numero, semana.anio, precio, confirmar.takeIf { it }))
        }) { alGuardar() }
        false
    }
}
