package com.queseria.app.ui.cobros

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraTabsBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.CuentaPorCobrar
import com.queseria.app.models.CuentasPorCobrar
import com.queseria.app.ui.common.Tabla
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.dp
import com.queseria.app.ui.common.fechaConMes
import com.queseria.app.util.Formato

/**
 * Cuentas por cobrar: las ventas fiadas que todavía deben algo (la más antigua primero), como tabla:
 * Cliente (fija) | Fecha | Días | Total | Abonado | Debe, con el total que te deben. Arriba, lo que debe cada
 * cliente. Tocar una venta = ver sus abonos y registrar uno. Necesita internet (sin conexión muestra lo guardado).
 */
class CuentasPorCobrarFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraTabsBinding

    private val tabla = Tabla<CuentaPorCobrar>(
        columnas = listOf(
            Tabla.Columna("Cliente", 124),
            Tabla.Columna("Debe", 112, numero = true),
            Tabla.Columna("Desde", 84),
            Tabla.Columna("Días", 76, numero = true),
            Tabla.Columna("Total", 112, numero = true),
            Tabla.Columna("Abonado", 112, numero = true)
        ),
        celdas = { c ->
            listOf(
                c.cliente ?: "Cliente",
                Formato.moneda(c.saldo),
                fechaConMes(c.fecha),
                if (c.dias == 1) "1 día" else "${c.dias} días",
                Formato.moneda(c.total),
                Formato.moneda(c.abonado)
            )
        },
        nota = { c -> c.abonos?.size?.takeIf { it > 0 }?.let { if (it == 1) "1 abono" else "$it abonos" } },
        colorCelda = { c, i ->
            when {
                i == 1 -> R.color.ambar_advertencia
                i == 3 && c.dias > 30 -> R.color.rojo_error
                else -> null
            }
        },
        alTocar = { c -> abrirAbonos(c.comoCobro()) { cargar() } }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraTabsBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cabecera.tabs.visibility = View.GONE
        cabecera.tvResumen.visibility = View.VISIBLE
        cabecera.tvResumen.text = "Cargando…"
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
        conApi({ RetrofitClient.api.getCuentasPorCobrar() }, binding.swipeRefresh, alFallar = {
            tabla.mostrar(emptyList(), null, "No se pudieron cargar las cuentas por cobrar.\nDesliza hacia abajo para reintentar.")
        }) { d -> mostrar(d ?: CuentasPorCobrar()) }
    }

    private fun mostrar(d: CuentasPorCobrar) {
        val cuentas = d.cuentas.orEmpty()
        val clientes = d.clientes.orEmpty()
        cabecera.tvResumen.text = if (cuentas.isEmpty()) "Nadie te debe: todas las ventas están pagadas."
        else buildString {
            append("Te deben ${Formato.moneda(d.total)} · ${cuentas.size} ${if (cuentas.size == 1) "venta" else "ventas"} de ")
            append("${clientes.size} ${if (clientes.size == 1) "cliente" else "clientes"}")
            clientes.take(5).forEach { c ->
                append("\n• ${c.cliente}: ${Formato.moneda(c.debe)} (desde el ${fechaConMes(c.desde)})")
            }
            if (clientes.size > 5) append("\n• y ${clientes.size - 5} más")
            append("\nToca una venta para ver sus abonos o registrar uno.")
        }
        tabla.mostrar(
            cuentas,
            totales = if (cuentas.isEmpty()) null else listOf(
                "Total",
                Formato.moneda(d.total),
                "", "",
                Formato.moneda(cuentas.sumOf { it.total }),
                Formato.moneda(cuentas.sumOf { it.abonado })
            ),
            vacio = "No hay ventas pendientes por cobrar.\nAl registrar una venta, marca «Pendiente por pagar» si te pagan después."
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
