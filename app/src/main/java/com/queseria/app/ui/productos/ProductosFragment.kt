package com.queseria.app.ui.productos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.DialogProductoBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Producto
import com.queseria.app.models.ProductoRequest
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.toast
import com.queseria.app.util.Formato

/**
 * Catálogo de productos (tipos de queso): crear, editar y eliminar.
 * Tocar = editar · mantener presionado = eliminar.
 */
class ProductosFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!

    private val adapter = FilaAdapter<Producto>(
        aFila = { p ->
            Fila(
                titulo = p.nombre,
                subtitulo = if (p.precioPorKg > 0) "${Formato.moneda(p.precioPorKg)} / kg" else "Sin precio definido",
                detalle = p.descripcion,
                valor = Formato.kg(p.stockKg),
                colorValor = if (p.stockBajo) R.color.rojo_error else R.color.verde_oscuro,
                etiqueta = if (p.stockBajo) "Stock bajo" else null,
                colorEtiqueta = R.color.rojo_error,
                icono = R.drawable.ic_queso,
                colorIcono = if (p.stockBajo) R.color.rojo_error else R.color.verde_principal
            )
        },
        alTocar = { abrirFormulario(it) },
        alMantener = { eliminar(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = "Nuevo queso"
        binding.fabAgregar.setOnClickListener { abrirFormulario(null) }
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getProductos() }, binding.swipeRefresh, alFallar = {
            binding.tvVacio.text = "No se pudieron cargar los productos.\nDesliza hacia abajo para reintentar."
            binding.tvVacio.visibility = View.VISIBLE
        }) { lista ->
            val productos = lista.orEmpty()
            adapter.actualizar(productos)
            binding.tvVacio.text = "Aún no tienes productos.\nToca + Nuevo queso para agregar el primero."
            binding.tvVacio.visibility = if (productos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Formulario para crear ([producto] = null) o editar un producto. */
    private fun abrirFormulario(producto: Producto?) {
        val f = DialogProductoBinding.inflate(layoutInflater)
        if (producto != null) {
            f.etNombre.setText(producto.nombre)
            f.etDescripcion.setText(producto.descripcion)
            if (producto.precioPorKg > 0) f.etPrecio.setText(Math.round(producto.precioPorKg).toString())
            if (producto.stockMinimoKg > 0) f.etStockMinimo.setText(producto.stockMinimoKg.toString().removeSuffix(".0"))
            // El stock de un producto existente se cambia desde Inventario (queda en el historial)
            f.lblStockInicial.visibility = View.GONE
            f.etStockInicial.visibility = View.GONE
        }

        mostrarFormulario(if (producto == null) "Nuevo producto" else "Editar producto", f.root) { dialogo ->
            val nombre = f.etNombre.text.toString().trim()
            val precio = Formato.leerNumero(f.etPrecio.text) ?: 0.0
            val minimo = Formato.leerNumero(f.etStockMinimo.text) ?: 0.0
            val stockInicial = Formato.leerNumero(f.etStockInicial.text) ?: 0.0

            when {
                nombre.isEmpty() -> f.etNombre.error = "Escribe el nombre"
                precio < 0 || minimo < 0 || stockInicial < 0 -> toast("Los valores no pueden ser negativos")
                else -> {
                    val request = ProductoRequest(
                        nombre = nombre,
                        descripcion = f.etDescripcion.text.toString().trim().ifEmpty { null },
                        precioPorKg = precio,
                        stockMinimoKg = minimo,
                        stockInicialKg = if (producto == null) stockInicial else null
                    )
                    if (producto == null) {
                        guardarEnApi(dialogo, "Producto creado", { RetrofitClient.api.crearProducto(request) }) { cargar() }
                    } else {
                        guardarEnApi(dialogo, "Producto actualizado", {
                            RetrofitClient.api.actualizarProducto(producto.id, request)
                        }) { cargar() }
                    }
                }
            }
            false
        }
    }

    private fun eliminar(producto: Producto) {
        confirmar(
            "Eliminar producto",
            "¿Eliminar \"${producto.nombre}\"? También se borrará su historial de inventario."
        ) {
            conApi({ RetrofitClient.api.eliminarProducto(producto.id) }) {
                toast("Producto eliminado")
                cargar()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
