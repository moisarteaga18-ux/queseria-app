package com.queseria.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.CabeceraVentasBinding
import com.queseria.app.databinding.DialogVentaBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Cliente
import com.queseria.app.models.Precio
import com.queseria.app.models.Producto
import com.queseria.app.models.Venta
import com.queseria.app.models.VentaRequest
import com.queseria.app.ui.clientes.abrirFormularioCliente
import com.queseria.app.ui.common.ListaPorDias
import com.queseria.app.ui.common.NavegadorSemana
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.elegirFecha
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.toast
import com.queseria.app.ui.ventas.TipoVenta
import com.queseria.app.ui.ventas.Unidad
import com.queseria.app.ui.ventas.filaVenta
import com.queseria.app.ui.ventas.total
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Pantalla de Ventas:
 * - Ventas de una semana (viernes a jueves, con flechas) agrupadas por día, y el resumen de la semana
 *   (total, kg y lb, número de ventas, minorista y mayorista).
 * - Registrar venta: cliente registrado (obligatorio), producto (el backend descuenta el inventario),
 *   tipo (minorista / mayorista), cantidad y precio (el de la semana en Precios, según el tipo).
 * Tocar una venta = corregirla (el backend ajusta el inventario).
 * Mantener presionada una venta = eliminarla (devuelve el queso al inventario).
 */
class VentasFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    private lateinit var cabecera: CabeceraVentasBinding

    private lateinit var navegador: NavegadorSemana
    /** Precios de cada semana ya consultada ("anio-semana" → precio; null = esa semana no tiene). */
    private val preciosSemana = mutableMapOf<String, Precio?>()

    // Ventas de la semana agrupadas por día (viernes a jueves), con el subtotal de cada día
    private val adapter = ListaPorDias<Venta>(
        fechaDe = { Formato.soloFecha(it.fecha) },
        subtotal = { del -> "${Formato.moneda(del.sumOf { it.total })} · ${Formato.kg(del.sumOf { Formato.aKg(it.cantidad, it.unidad) })}" },
        aFila = { filaVenta(it) },
        alTocar = { abrirFormulario(it) },
        alMantener = { eliminar(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        cabecera = CabeceraVentasBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Nueva venta"
        binding.fabAgregar.setOnClickListener { abrirFormulario(null) }

        // Semana de viernes a jueves (por defecto la actual); cambiarla vuelve a cargar
        navegador = NavegadorSemana(cabecera.semana) { cargar() }
        cargar()

        // Desde Inicio → "Nueva venta" se abre el formulario directamente
        if (arguments?.getBoolean(InicioFragment.ARG_NUEVO) == true) {
            arguments?.remove(InicioFragment.ARG_NUEVO)
            abrirFormulario(null)
        }
    }

    private fun cargar() {
        val semana = navegador.semana
        // Del viernes al final del jueves (como Reportes y Ganancias)
        conApi({
            RetrofitClient.api.getVentas(limit = 1000, desde = semana.inicio.toString(), hasta = "${semana.fin}T23:59:59.999Z")
        }, binding.swipeRefresh, alFallar = {
            binding.tvVacio.text = "No se pudieron cargar las ventas.\nDesliza hacia abajo para reintentar."
            binding.tvVacio.visibility = View.VISIBLE
        }) {
            // Si mientras cargaba se eligió otra semana, esta respuesta ya no sirve
            if (semana == navegador.semana) mostrar(semana, it.orEmpty())
        }
    }

    private fun mostrar(semana: Semana, ventas: List<Venta>) {
        adapter.mostrar(semana, ventas)
        val kg = ventas.sumOf { Formato.aKg(it.cantidad, it.unidad) }
        cabecera.tvTotal.text = Formato.moneda(ventas.sumOf { it.total })
        cabecera.tvNumVentas.text = "vendido en la semana · ${ventas.size} ${if (ventas.size == 1) "venta" else "ventas"}"
        cabecera.tvCantidad.text = Formato.kg(kg)
        cabecera.tvLibras.text = "${Formato.numero(Formato.kgALibras(kg))} lb"
        // Minorista ("libras") y mayorista por separado
        val (mayoristas, minoristas) = ventas.partition { it.tipoVenta == TipoVenta.MAYORISTA }
        fun detalle(lista: List<Venta>) =
            "${Formato.kg(lista.sumOf { Formato.aKg(it.cantidad, it.unidad) })} · ${lista.size} ${if (lista.size == 1) "venta" else "ventas"}"
        cabecera.tvMinorista.text = Formato.moneda(minoristas.sumOf { it.total })
        cabecera.tvMinoristaDetalle.text = detalle(minoristas)
        cabecera.tvMayorista.text = Formato.moneda(mayoristas.sumOf { it.total })
        cabecera.tvMayoristaDetalle.text = detalle(mayoristas)
        // La lista siempre muestra los 7 días (los vacíos dicen "Sin registros")
        binding.tvVacio.visibility = View.GONE
    }

    /**
     * Precios (minorista y mayorista) de la semana de [fecha]; se consultan una vez por semana.
     * Si la semana no tiene precios el backend responde 404: no es un error para mostrar.
     */
    private fun precioDeSemana(fecha: String, alCargar: (Precio?) -> Unit) {
        val s = Semana.de(LocalDate.parse(fecha))
        val clave = "${s.anio}-${s.numero}"
        if (preciosSemana.containsKey(clave)) return alCargar(preciosSemana[clave])
        viewLifecycleOwner.lifecycleScope.launch {
            val precio = (llamarApi { RetrofitClient.api.getPrecioActual(fecha) } as? Resultado.Exito)?.data
            preciosSemana[clave] = precio
            if (view != null) alCargar(precio)
        }
    }

    /** [existente] = corregir una venta; null = registrar una nueva. */
    private fun abrirFormulario(existente: Venta?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val clientes = async { llamarApi { RetrofitClient.api.getClientes() } }
            val productos = async { llamarApi { RetrofitClient.api.getProductos() } }
            // Para proponer el producto de la última venta (si falla, simplemente no se propone)
            val ultima = async { if (existente == null) llamarApi { RetrofitClient.api.getVentas(limit = 1) } else null }
            val rC = clientes.await()
            val rP = productos.await()
            val ultimoProductoId = (ultima.await() as? Resultado.Exito)?.data?.firstOrNull()?.productoId
            if (view == null) return@launch
            val error = listOf(rC, rP).filterIsInstance<Resultado.Error>().firstOrNull()
            if (error != null) {
                manejarError(error)
                return@launch
            }
            mostrarFormularioVenta(
                (rC as Resultado.Exito).data.orEmpty(),
                (rP as Resultado.Exito).data.orEmpty(),
                existente,
                ultimoProductoId
            )
        }
    }

    private fun mostrarFormularioVenta(
        clientes: List<Cliente>,
        productos: List<Producto>,
        existente: Venta?,
        ultimoProductoId: String? = null
    ) {
        val f = DialogVentaBinding.inflate(layoutInflater)
        var fecha = existente?.let { Formato.soloFecha(it.fecha) } ?: Formato.hoyIso()
        f.tvFecha.text = Formato.fechaLegible(fecha)
        // Precios de la semana de la venta (cambian si se elige una fecha de otra semana)
        var precioSemana: Precio? = null
        var cargarPrecios: () -> Unit = {}
        f.tvFecha.setOnClickListener {
            elegirFecha(fecha) { fecha = it; f.tvFecha.text = Formato.fechaLegible(it); cargarPrecios() }
        }

        // Solo se vende a clientes registrados: se elige de la lista (o se agrega uno nuevo)
        var listaClientes = clientes
        fun llenarClientes(seleccionarId: String? = null) {
            val nombres = listOf(if (listaClientes.isEmpty()) "— Primero agrega un cliente —" else "— Elige el cliente —") +
                listaClientes.map { it.nombre }
            f.spCliente.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nombres)
            // Al corregir: el cliente de la venta (por id o, en ventas antiguas, por nombre)
            val i = listaClientes.indexOfFirst { it.id == seleccionarId }.takeIf { it >= 0 }
                ?: existente?.takeIf { seleccionarId == null }?.let { v ->
                    listaClientes.indexOfFirst { it.id == v.clienteId || (v.clienteId == null && it.nombre.equals(v.cliente, ignoreCase = true)) }
                } ?: -1
            if (i >= 0) f.spCliente.setSelection(i + 1)
        }
        fun clienteElegido(): Cliente? = listaClientes.getOrNull(f.spCliente.selectedItemPosition - 1)
        llenarClientes()
        f.btnNuevoCliente.setOnClickListener {
            abrirFormularioCliente(null) { nuevo ->
                if (nuevo.id.isNotEmpty()) {
                    listaClientes = (listaClientes + nuevo).sortedBy { it.nombre.lowercase() }
                    llenarClientes(nuevo.id)
                }
            }
        }

        f.spProducto.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("— Sin producto (no descuenta) —") + productos.map { it.nombre }
        )
        // Venta nueva: el producto de la última venta o, si solo hay uno, ese
        if (existente == null) {
            val i = productos.indexOfFirst { it.id == ultimoProductoId }
            if (i >= 0) f.spProducto.setSelection(i + 1)
            else if (productos.size == 1) f.spProducto.setSelection(1)
        }
        fun producto() = productos.getOrNull(f.spProducto.selectedItemPosition - 1)
        fun enLibras() = f.rbLb.isChecked

        // Al corregir: los datos de la venta (antes de conectar los listeners, para que no los cambien)
        existente?.let { v ->
            productos.indexOfFirst { it.id == v.productoId }.takeIf { it >= 0 }?.let { f.spProducto.setSelection(it + 1) }
            if (v.tipoVenta == TipoVenta.MAYORISTA) f.rbMayorista.isChecked = true else f.rbLibras.isChecked = true
            if (v.unidad == Unidad.LIBRAS) f.rbLb.isChecked = true else f.rbKg.isChecked = true
            f.etCantidad.setText(Formato.campo(v.cantidad))
            f.etPrecio.setText(Formato.campo(v.precioUnitario))
        }
        /** Stock disponible para esta venta: al corregirla, lo que ella misma sacó vuelve a contar. */
        fun disponible(p: Producto) = p.stockKg +
            if (existente != null && existente.productoId == p.id) Formato.aKg(existente.cantidad, existente.unidad) else 0.0

        // El precio sale de Precios: el minorista o el mayorista de la semana de la venta, en la
        // unidad elegida. Sin precio de la semana: el del producto (solo minorista).
        // [forzar] = el usuario cambió el tipo, la unidad o la fecha: se pone el precio aunque haya
        // uno escrito; si no, solo se reemplaza si no escribió un precio distinto.
        var ultimaSugerencia: String? = null
        fun sugerirPrecio(forzar: Boolean = false) {
            val mayorista = f.rbMayorista.isChecked
            val semana = Semana.de(LocalDate.parse(fecha))
            val deLaSemana = precioSemana?.let { if (mayorista) it.precioMayoristaPorKg else it.precioPorKg }
            val delProducto = producto()?.precioPorKg?.takeIf { it > 0 && !mayorista }
            val porKg = deLaSemana ?: delProducto
            val tipo = if (mayorista) "mayorista" else "minorista"
            f.tvOrigenPrecio.text = when {
                deLaSemana != null -> "Precio $tipo de la semana ${semana.numero}"
                delProducto != null -> "Precio del producto: la semana ${semana.numero} no tiene precio $tipo (defínelo en Precios)"
                else -> "La semana ${semana.numero} no tiene precio $tipo: escríbelo o defínelo en Precios"
            }
            val sugerido = porKg?.let { Math.round(if (enLibras()) it / Formato.LIBRAS_POR_KG else it).toString() }
            val actual = f.etPrecio.text.toString()
            if (sugerido != null && (forzar || actual.isEmpty() || actual == ultimaSugerencia)) {
                f.etPrecio.setText(sugerido)
                ultimaSugerencia = sugerido
            } else if (sugerido == null && forzar && actual == ultimaSugerencia) {
                // El precio que había era de otro tipo: no se deja uno que no corresponde
                f.etPrecio.setText("")
                ultimaSugerencia = null
            }
        }
        cargarPrecios = {
            val pedida = fecha
            precioDeSemana(pedida) { p ->
                if (pedida == fecha) {
                    precioSemana = p
                    // Al abrir una venta para corregirla se respeta su precio
                    sugerirPrecio(forzar = existente == null || f.etPrecio.text.toString() == ultimaSugerencia)
                }
            }
        }

        fun actualizarTextos() {
            f.lblPrecio.text = if (enLibras()) "Precio por libra *" else "Precio por kg *"
            val cantidad = Formato.leerNumero(f.etCantidad.text) ?: 0.0
            val precio = Formato.leerNumero(f.etPrecio.text) ?: 0.0
            f.tvTotal.text = "Total: ${Formato.moneda(cantidad * precio)}"
            f.tvStock.text = producto()?.let { "Disponible: ${Formato.kg(disponible(it))} (${Formato.numero(Formato.kgALibras(disponible(it)))} lb)" }
            f.tvStock.visibility = if (producto() == null) View.GONE else View.VISIBLE
        }

        f.rgTipo.setOnCheckedChangeListener { _, id ->
            // Minorista → libras; mayorista → kilos (se puede cambiar a mano). El precio cambia al tipo elegido.
            val unidad = if (id == f.rbLibras.id) f.rbLb else f.rbKg
            if (unidad.isChecked) { sugerirPrecio(forzar = true); actualizarTextos() } else unidad.isChecked = true
        }
        // Al elegir el cliente, la venta toma su tipo (mayorista o minorista); no al abrir una venta para corregirla
        var ultimaPosCliente = f.spCliente.selectedItemPosition
        f.spCliente.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == ultimaPosCliente) return
                ultimaPosCliente = pos
                when (clienteElegido()?.tipo) {
                    Cliente.MAYORISTA -> f.rbMayorista.isChecked = true
                    Cliente.MINORISTA -> f.rbLibras.isChecked = true
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        f.rgUnidad.setOnCheckedChangeListener { _, _ -> sugerirPrecio(forzar = true); actualizarTextos() }
        f.spProducto.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { sugerirPrecio(); actualizarTextos() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        f.etCantidad.doAfterTextChanged { actualizarTextos() }
        f.etPrecio.doAfterTextChanged { actualizarTextos() }
        sugerirPrecio()
        actualizarTextos()
        cargarPrecios()

        mostrarFormulario(if (existente == null) "Nueva venta" else "Corregir venta", f.root) { dialogo ->
            val cliente = clienteElegido()
            if (cliente == null) {
                toast(if (listaClientes.isEmpty()) "Primero agrega el cliente con «+ Agregar cliente»" else "Elige a qué cliente le vendes")
                return@mostrarFormulario false
            }
            val cantidad = Formato.leerNumero(f.etCantidad.text)
            val precio = Formato.leerNumero(f.etPrecio.text)
            if (cantidad == null || cantidad <= 0) {
                f.etCantidad.error = "Ingresa la cantidad"
                return@mostrarFormulario false
            }
            if (precio == null || precio <= 0) {
                f.etPrecio.error = "Ingresa el precio"
                return@mostrarFormulario false
            }
            val unidad = if (enLibras()) Unidad.LIBRAS else Unidad.KG
            val kg = Formato.aKg(cantidad, unidad)
            val prod = producto()
            if (prod != null && kg > disponible(prod) + 0.0001) {
                f.etCantidad.error = "Solo hay ${Formato.kg(disponible(prod))} de ${prod.nombre}"
                return@mostrarFormulario false
            }
            val request = VentaRequest(
                tipoVenta = if (f.rbMayorista.isChecked) TipoVenta.MAYORISTA else TipoVenta.LIBRAS,
                cliente = cliente.nombre,
                clienteId = cliente.id,
                productoId = prod?.id,
                cantidad = cantidad,
                unidad = unidad,
                precioUnitario = precio,
                fecha = fecha
            )
            val id = existente?.id
            if (id == null) {
                guardarEnApi(dialogo, "Listo: venta de ${Formato.moneda(cantidad * precio)}", {
                    RetrofitClient.api.crearVenta(request)
                }) { cargar() }
            } else {
                // productoId "" = ya no descuenta inventario (null no cambiaría nada)
                guardarEnApi(dialogo, "Venta corregida: ${Formato.moneda(cantidad * precio)}", {
                    RetrofitClient.api.actualizarVenta(id, request.copy(productoId = prod?.id ?: ""))
                }) { cargar() }
            }
            false
        }
    }

    private fun eliminar(v: Venta) {
        val id = v.id ?: return
        confirmar(
            "Eliminar venta",
            "¿Eliminar la venta de ${Formato.moneda(v.total)} del ${Formato.fechaLegible(v.fecha)}?\n" +
                "Si descontó inventario, el queso se devolverá al stock."
        ) {
            conApi({ RetrofitClient.api.eliminarVenta(id) }) {
                toast("Venta eliminada")
                cargar()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
