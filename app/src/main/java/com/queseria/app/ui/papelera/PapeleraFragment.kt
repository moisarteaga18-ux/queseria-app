package com.queseria.app.ui.papelera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraTabsBinding
import com.queseria.app.databinding.DialogRevisarBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.EntradaPapelera
import com.queseria.app.ui.common.Tabla
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.dp
import com.queseria.app.ui.common.fechaConMes
import com.queseria.app.ui.common.restaurarDePapelera
import com.queseria.app.util.Formato
import java.time.Duration
import java.time.Instant

/**
 * Papelera: lo borrado (leche, producción, ventas, adelantos y liquidaciones anuladas) en los últimos 30 días.
 * Tocar un registro → ver sus datos → "Recuperar" (vuelve con sus mismos datos; el inventario y las
 * liquidaciones se ajustan solos). Pasados 30 días se borra solo de la papelera.
 */
class PapeleraFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraTabsBinding

    private val tabla = Tabla<EntradaPapelera>(
        columnas = listOf(
            Tabla.Columna("Borrado", 84),
            Tabla.Columna("Qué", 124),
            Tabla.Columna("Fecha", 84),
            Tabla.Columna("Nombre", 130),
            Tabla.Columna("Cantidad", 150),
            Tabla.Columna("Valor", 112, numero = true),
            Tabla.Columna("Quedan", 84, numero = true)
        ),
        celdas = { e ->
            val dias = diasQuedan(e)
            listOf(
                fechaConMes(e.borradoEn),
                e.tipoLegible,
                fechaConMes(e.resumen.fecha),
                e.resumen.nombre ?: "—",
                e.resumen.cantidad ?: "—",
                e.resumen.valor?.let { Formato.moneda(it) } ?: "—",
                if (dias == 1L) "1 día" else "$dias días"
            )
        },
        colorCelda = { e, i -> if (i == 6 && diasQuedan(e) <= 3) R.color.rojo_error else null },
        alTocar = { preguntarRecuperar(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraTabsBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cabecera.tabs.visibility = View.GONE
        cabecera.tvResumen.text = "Lo que borraste en los últimos 30 días. Toca un registro para recuperarlo."
        cabecera.tvResumen.visibility = View.VISIBLE
        binding.fabAgregar.visibility = View.GONE
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = tabla
        binding.rvLista.setPadding(dp(8), 0, dp(8), dp(24))
        tabla.crearEncabezado(binding.contenedorFijo)
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.tvVacio.visibility = View.GONE
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getPapelera() }, binding.swipeRefresh, alFallar = {
            tabla.mostrar(emptyList(), null, "No se pudo cargar la papelera.\nDesliza hacia abajo para reintentar.")
        }) { lista ->
            tabla.mostrar(lista.orEmpty(), null, "La papelera está vacía.\nLo que borres quedará aquí 30 días.")
        }
    }

    private fun diasQuedan(e: EntradaPapelera): Long =
        runCatching { (Duration.between(Instant.now(), Instant.parse(e.expiraEn)).toHours() + 23) / 24 }
            .getOrDefault(0L).coerceAtLeast(0L)

    /** Muestra el registro y pregunta si se recupera. */
    private fun preguntarRecuperar(e: EntradaPapelera) {
        val b = DialogRevisarBinding.inflate(layoutInflater)
        b.tvTitulo.text = "Recuperar: ${e.tipoLegible.lowercase()}"
        listOfNotNull(
            "Fecha" to fechaConMes(e.resumen.fecha),
            "Nombre" to (e.resumen.nombre ?: "—"),
            "Cantidad" to (e.resumen.cantidad ?: "—"),
            e.resumen.valor?.let { "Valor" to Formato.moneda(it) },
            "Borrado" to Formato.fechaLegible(e.borradoEn)
        ).forEach { (nombre, valor) ->
            b.contenedorDatos.addView(TextView(requireContext()).apply {
                text = "$nombre:  $valor"
                textSize = 15f
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        val aviso = when (e.tipo) {
            "venta" -> "El queso se vuelve a descontar del inventario (si no alcanza el stock, no se recupera). " +
                "Si tenía abonos, vuelven con ella."
            "abono" -> "Vuelve a la venta (si ya se pagó completa por otro lado, no se recupera)."
            "produccion" -> "El queso se vuelve a sumar al inventario."
            "liquidacion" -> "Vuelve con su mismo número, pendiente de pago."
            else -> null
        }
        b.tvAviso.text = aviso
        b.tvAviso.visibility = if (aviso == null) View.GONE else View.VISIBLE
        b.tvPregunta.text = "¿Recuperar este registro?"
        val dialogo = AlertDialog.Builder(requireContext()).setView(b.root).create()
        b.btnPrincipal.text = "Sí, recuperar"
        b.btnPrincipal.setOnClickListener {
            dialogo.dismiss()
            restaurarDePapelera(e.id, "Listo: se recuperó") { cargar() }
        }
        b.btnSecundario.text = "Cancelar"
        b.btnSecundario.setOnClickListener { dialogo.dismiss() }
        dialogo.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
