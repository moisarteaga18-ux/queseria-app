package com.queseria.app.ui.inventario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.CabeceraInventarioBinding
import com.queseria.app.databinding.DialogMovimientoBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.MovimientoInventario
import com.queseria.app.models.MovimientoRequest
import com.queseria.app.models.Producto
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.toast
import com.queseria.app.util.Formato

/**
 * Inventario de quesos:
 * - Pestaña "Stock": existencias por producto, con alerta de stock bajo.
 * - Pestaña "Movimientos": historial de entradas, salidas y ajustes.
 * Tocar un producto o el botón + registra un movimiento.
 */
class InventarioFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraInventarioBinding

    private var productos: List<Producto> = emptyList()
    private var movimientos: List<MovimientoInventario> = emptyList()

    private val adapterStock = FilaAdapter<Producto>(
        aFila = { p ->
            Fila(
                titulo = p.nombre,
                subtitulo = "${Formato.numero(Formato.kgALibras(p.stockKg))} lb" +
                    if (p.stockMinimoKg > 0) " · mínimo ${Formato.kg(p.stockMinimoKg)}" else "",
                valor = Formato.kg(p.stockKg),
                colorValor = if (p.stockBajo) R.color.rojo_error else R.color.verde_oscuro,
                etiqueta = if (p.stockBajo) "Stock bajo" else null,
                colorEtiqueta = R.color.rojo_error,
                icono = R.drawable.ic_queso,
                colorIcono = if (p.stockBajo) R.color.rojo_error else R.color.verde_principal
            )
        },
        alTocar = { abrirMovimiento(it) }
    )

    private val adapterMovimientos = FilaAdapter<MovimientoInventario>(
        aFila = { m ->
            val signo = if (m.cantidadKg >= 0) "+" else "−"
            Fila(
                titulo = m.nombreProducto,
                subtitulo = "${tipoLegible(m.tipo)} · ${Formato.fechaLegible(m.fecha)}",
                detalle = m.motivo,
                valor = "$signo${Formato.kg(kotlin.math.abs(m.cantidadKg))}",
                colorValor = if (m.cantidadKg >= 0) R.color.verde_exito else R.color.rojo_error,
                icono = when (m.tipo) {
                    MovimientoInventario.ENTRADA -> R.drawable.ic_entrada
                    MovimientoInventario.SALIDA -> R.drawable.ic_salida
                    else -> R.drawable.ic_ajuste
                },
                colorIcono = when (m.tipo) {
                    MovimientoInventario.ENTRADA -> R.color.verde_exito
                    MovimientoInventario.SALIDA -> R.color.rojo_error
                    else -> R.color.azul_info
                }
            )
        },
        alTocar = { m -> if (esManual(m)) corregirMovimiento(m) else avisarOrigen(m) },
        alMantener = { m -> if (esManual(m)) eliminarMovimiento(m) else avisarOrigen(m) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraInventarioBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Entrada o salida"
        binding.fabAgregar.setOnClickListener { abrirMovimiento(null) }

        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Stock"))
        cabecera.tabs.addTab(cabecera.tabs.newTab().setText("Movimientos"))
        cabecera.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = mostrarPestana()
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        mostrarPestana()
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getProductos() }, binding.swipeRefresh) {
            productos = it.orEmpty()
            adapterStock.actualizar(productos)
            actualizarResumen()
            mostrarPestana()
        }
        // Stock de la semana estimado con la merma promedio (la merma se calcula sola al cierre)
        conApi({ RetrofitClient.api.getMerma() }) { m ->
            val a = m?.actual ?: return@conApi
            cabecera.tvEstimado.visibility = if (a.kgProducidos + a.stockInicialKg <= 0) View.GONE else View.VISIBLE
            cabecera.tvEstimado.text = "Esta semana: ${Formato.kg(a.stockInicialKg)} inicial + ${Formato.kg(a.kgProducidos)} producidos − " +
                "${Formato.kg(a.kgVendidos)} vendidos − merma est. ${Formato.kg(a.mermaEstimadaKg)} (${Formato.numero(m.promedioPct, 1)} %) = " +
                "${Formato.kg(a.stockEstimadoKg)}"
        }
        conApi({ RetrofitClient.api.getMovimientos() }) {
            movimientos = it.orEmpty()
            adapterMovimientos.actualizar(movimientos)
            mostrarPestana()
        }
    }

    private fun mostrarPestana() {
        val enStock = cabecera.tabs.selectedTabPosition != 1
        if (enStock) {
            if (binding.rvLista.adapter !== adapterStock) binding.rvLista.adapter = adapterStock
            binding.tvVacio.text = "Aún no tienes productos.\nCréalos en el menú de Inicio → Productos."
            binding.tvVacio.visibility = if (productos.isEmpty()) View.VISIBLE else View.GONE
        } else {
            if (binding.rvLista.adapter !== adapterMovimientos) binding.rvLista.adapter = adapterMovimientos
            binding.tvVacio.text = "Aún no hay movimientos de inventario.\nSe crean solos al registrar producción y ventas."
            binding.tvVacio.visibility = if (movimientos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun actualizarResumen() {
        val totalKg = productos.sumOf { it.stockKg.coerceAtLeast(0.0) }
        val valor = productos.sumOf { it.stockKg.coerceAtLeast(0.0) * it.precioPorKg }
        cabecera.tvTotalKg.text = Formato.kg(totalKg)
        cabecera.tvTotalLb.text = "${Formato.numero(Formato.kgALibras(totalKg))} lb en total"
        cabecera.tvValor.text = Formato.moneda(valor)

        val bajos = productos.filter { it.stockBajo }
        cabecera.tvAlertaStock.visibility = if (bajos.isEmpty()) View.GONE else View.VISIBLE
        cabecera.tvAlertaStock.text = "Stock bajo: " + bajos.joinToString { it.nombre }
    }

    /**
     * Registra una entrada o salida. Si [producto] es null, se elige en el formulario.
     * (No hay "ajuste": la merma se calcula sola en el cierre semanal.)
     */
    private fun abrirMovimiento(producto: Producto?) {
        if (productos.isEmpty()) {
            toast("Primero crea un producto")
            findNavController().navigate(R.id.nav_productos)
            return
        }
        val f = DialogMovimientoBinding.inflate(layoutInflater)
        f.spProducto.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            productos.map { it.nombre }
        )
        producto?.let { p -> f.spProducto.setSelection(productos.indexOfFirst { it.id == p.id }.coerceAtLeast(0)) }

        fun seleccionado() = productos[f.spProducto.selectedItemPosition]
        fun refrescarTextos() {
            f.tvStockActual.text = "Stock actual: ${Formato.kg(seleccionado().stockKg)}"
        }
        f.rgTipo.setOnCheckedChangeListener { _, _ -> refrescarTextos() }
        f.spProducto.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = refrescarTextos()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        refrescarTextos()

        mostrarFormulario("Movimiento de inventario", f.root) { dialogo ->
            val cantidad = Formato.leerNumero(f.etCantidad.text)
            val p = seleccionado()
            if (cantidad == null || cantidad <= 0) {
                f.etCantidad.error = "Cantidad inválida"
                return@mostrarFormulario false
            }
            val kg = if (f.rbLb.isChecked) Formato.librasAKg(cantidad) else cantidad
            val tipo = if (f.rbSalida.isChecked) MovimientoInventario.SALIDA else MovimientoInventario.ENTRADA
            if (tipo == MovimientoInventario.SALIDA && kg > p.stockKg + 0.0001) {
                f.etCantidad.error = "Solo hay ${Formato.kg(p.stockKg)} en stock"
                return@mostrarFormulario false
            }
            val request = MovimientoRequest(
                productoId = p.id,
                tipo = tipo,
                cantidadKg = kg,
                motivo = f.etMotivo.text.toString().trim().ifEmpty { null }
            )
            guardarEnApi(dialogo, "Inventario actualizado", { RetrofitClient.api.crearMovimiento(request) }) { cargar() }
            false
        }
    }

    /** Entradas y salidas hechas a mano (incluido el stock inicial); no las de ventas o producción. */
    private fun esManual(m: MovimientoInventario) =
        m.referencia == null && (m.tipo == MovimientoInventario.ENTRADA || m.tipo == MovimientoInventario.SALIDA)

    private fun avisarOrigen(m: MovimientoInventario) {
        toast(
            if (m.tipo == MovimientoInventario.AJUSTE) "Los ajustes anteriores no se pueden cambiar"
            else "Este movimiento viene de una venta o una producción: corrígelo desde allí"
        )
    }

    /** Corrige tipo, cantidad o motivo de un movimiento manual (el producto no cambia). */
    private fun corregirMovimiento(m: MovimientoInventario) {
        val f = DialogMovimientoBinding.inflate(layoutInflater)
        f.spProducto.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf(m.nombreProducto)
        )
        f.spProducto.isEnabled = false
        if (m.cantidadKg < 0) f.rbSalida.isChecked = true else f.rbEntrada.isChecked = true
        f.rbKg.isChecked = true
        f.etCantidad.setText(Formato.campo(kotlin.math.abs(m.cantidadKg)))
        f.etMotivo.setText(m.motivo.orEmpty())
        // Lo que el stock tendría sin este movimiento (para avisar antes de enviar)
        val stock = productos.firstOrNull { it.id == m.productoId }?.stockKg
        f.tvStockActual.text = stock?.let { "Stock actual: ${Formato.kg(it)}" }.orEmpty()

        mostrarFormulario("Corregir movimiento", f.root) { dialogo ->
            val cantidad = Formato.leerNumero(f.etCantidad.text)
            if (cantidad == null || cantidad <= 0) {
                f.etCantidad.error = "Cantidad inválida"
                return@mostrarFormulario false
            }
            val kg = if (f.rbLb.isChecked) Formato.librasAKg(cantidad) else cantidad
            val tipo = if (f.rbSalida.isChecked) MovimientoInventario.SALIDA else MovimientoInventario.ENTRADA
            val nuevo = if (tipo == MovimientoInventario.SALIDA) -kg else kg
            if (stock != null && stock - m.cantidadKg + nuevo < -0.0001) {
                f.etCantidad.error = "No alcanza: quedarían ${Formato.kg(stock - m.cantidadKg + nuevo)}"
                return@mostrarFormulario false
            }
            val request = MovimientoRequest(
                productoId = m.productoId,
                tipo = tipo,
                cantidadKg = kg,
                motivo = f.etMotivo.text.toString().trim().ifEmpty { null }
            )
            guardarEnApi(dialogo, "Movimiento corregido", { RetrofitClient.api.actualizarMovimiento(m.id, request) }) { cargar() }
            false
        }
    }

    private fun eliminarMovimiento(m: MovimientoInventario) {
        val texto = "${tipoLegible(m.tipo).lowercase()} de ${Formato.kg(kotlin.math.abs(m.cantidadKg))} de ${m.nombreProducto}"
        // Quitar una entrada cuyo queso ya se vendió o se sacó dejaría el stock negativo
        val stock = productos.firstOrNull { it.id == m.productoId }?.stockKg
        if (stock != null && stock - m.cantidadKg < -0.0001) {
            confirmar(
                "No se puede eliminar",
                "Hay ${Formato.kg(stock)} de ${m.nombreProducto} y esta entrada es de ${Formato.kg(m.cantidadKg)}: " +
                    "parte de ese queso ya se vendió o se sacó. Si la cantidad estaba mal, tócala para corregirla.",
                textoSi = "Entendido"
            ) {}
            return
        }
        confirmar("Eliminar movimiento", "¿Eliminar la $texto? El stock vuelve a quedar como si no se hubiera hecho.") {
            conApi({ RetrofitClient.api.eliminarMovimiento(m.id) }) {
                toast("Movimiento eliminado")
                cargar()
            }
        }
    }

    private fun tipoLegible(tipo: String) = when (tipo) {
        MovimientoInventario.ENTRADA -> "Entrada"
        MovimientoInventario.SALIDA -> "Salida"
        else -> "Ajuste (anterior)"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
