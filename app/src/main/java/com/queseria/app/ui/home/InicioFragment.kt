package com.queseria.app.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.queseria.app.MainActivity
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.FragmentInicioBinding
import com.queseria.app.databinding.ItemAccionDiariaBinding
import com.queseria.app.databinding.ItemTarjetaGrandeBinding
import com.queseria.app.databinding.ItemTarjetaPequenaBinding
import com.queseria.app.models.ResumenHoy
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.liquidaciones.registrarAdelantoODescuento
import com.queseria.app.ui.merma.preguntarCierre
import com.google.android.material.card.MaterialCardView
import com.queseria.app.util.Formato
import java.util.Calendar

/**
 * Inicio = menú principal de la app (diseño de imagenes_quesodata).
 * Cabecera con la foto del campo, saludo y fecha; el resumen del día (leche, queso, ventas y lo que
 * se debe a los ganaderos); accesos de hoy (con Adelanto o descuento); tarjetas grandes con ilustración;
 * Reportes; "Más opciones" y la barra inferior. Tarjetas crema con sombra (guía de diseño). Configuración reúne Mi cuenta, Cambiar contraseña, Apariencia y Cerrar sesión.
 */
class InicioFragment : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    // El detalle del queso se arma con dos llamadas (resumen del día y merma), que llegan en cualquier orden
    private var kgHoy = 0.0
    private var stockEstimado: Double? = null

    /**
     * Una tarjeta del menú. [args] se pasan al abrir la pantalla (por ejemplo, abrir el formulario de una vez).
     * [destino] null = no abre una pantalla: hace [accion] (Adelanto o descuento, Apariencia, Cerrar sesión).
     * [fondo] es el color de la tarjeta y [fondoDegradado]
     * lo reemplaza cuando la tarjeta lleva degradado; [alto] es el alto de la ilustración en dp.
     */
    private data class Opcion(
        val titulo: String,
        val descripcion: String,
        @DrawableRes val icono: Int,
        @IdRes val destino: Int?,
        @ColorRes val circulo: Int,
        @ColorRes val fondo: Int = R.color.superficie,
        @DrawableRes val ilustracion: Int? = null,
        val alto: Int = 0,
        val args: Bundle? = null,
        @DrawableRes val fondoDegradado: Int? = null,
        val claro: Boolean = false,
        val accion: (() -> Unit)? = null
    )

    // Hoy: lo que se registra todos los días
    private val hoy by lazy {
        listOf(
            Opcion("Registrar leche", "Litros de cada ganadero", R.drawable.ic_leche, R.id.nav_leche,
                R.color.qd_c_liquidacion, args = bundleOf(ARG_NUEVO to true)),
            Opcion("Producción del día", "Leche convertida en queso", R.drawable.ic_produccion, R.id.nav_produccion,
                R.color.qd_c_precios, args = bundleOf(ARG_NUEVO to true)),
            Opcion("Adelanto o descuento", "A un ganadero, eligiendo la semana en que se descuenta", R.drawable.ic_pagos, null,
                R.color.qd_c_cuenta, accion = { registrarAdelantoODescuento { cargar() } })
        )
    }

    // Tarjetas grandes (los colores salen de imagenes_quesodata/LEEME.txt)
    private val principales by lazy {
        listOf(
            Opcion("Nueva venta", "Cliente, queso y precio", R.drawable.ic_qd_venta, R.id.nav_ventas,
                R.color.qd_c_venta, ilustracion = R.drawable.img_nueva_venta_queso, alto = 128,
                args = bundleOf(ARG_NUEVO to true), fondoDegradado = R.drawable.bg_qd_venta, claro = true),
            Opcion("Inventario", "Queso disponible", R.drawable.ic_qd_inventario, R.id.nav_inventario,
                R.color.qd_c_inventario, R.color.qd_inventario, R.drawable.img_inventario_estante, 120),
            Opcion("Liquidación a ganaderos", "Pagar la leche de la semana", R.drawable.ic_qd_liquidacion, R.id.nav_liquidaciones,
                R.color.qd_c_liquidacion, R.color.qd_liquidacion, R.drawable.img_liquidacion_cantina, 118),
            Opcion("Precios", "Precio del queso por semana", R.drawable.ic_qd_precio, R.id.nav_precios,
                R.color.qd_c_precios, R.color.qd_precios, R.drawable.img_precios_quesos, 110)
        )
    }

    // Más opciones (tarjetas pequeñas). Configuración reúne Mi cuenta, Cambiar contraseña, Apariencia y Cerrar sesión.
    private val masOpciones by lazy {
        listOf(
            Opcion("Ganaderos", "", R.drawable.ic_qd_ganadero, R.id.nav_ganaderos,
                R.color.qd_c_ganaderos, R.color.qd_ganaderos, R.drawable.img_ganaderos_leche, 54),
            Opcion("Clientes", "", R.drawable.ic_qd_clientes, R.id.nav_clientes,
                R.color.qd_c_clientes, R.color.qd_clientes, R.drawable.img_clientes_apreton, 46),
            Opcion("Productos", "", R.drawable.ic_qd_productos, R.id.nav_productos,
                R.color.qd_c_productos, R.color.qd_productos, R.drawable.img_productos_queso, 46),
            Opcion("Configuración", "", R.drawable.ic_qd_configuracion, null,
                R.color.qd_c_configuracion, R.color.qd_configuracion, R.drawable.img_configuracion_engranajes, 36,
                accion = { abrirConfiguracion() })
        )
    }

    // Configuración (se abre desde su tarjeta, el menú ≡ de la cabecera y "Cuenta" de la barra inferior)
    private val configuracion by lazy {
        listOf(
            Opcion("Mi cuenta", "Datos del negocio, teléfono y NIT", R.drawable.ic_qd_cuenta, R.id.nav_perfil, R.color.qd_c_cuenta),
            Opcion("Cambiar contraseña", "Cierra la sesión en los otros teléfonos", R.drawable.ic_qd_candado, R.id.nav_contrasena,
                R.color.qd_c_contrasena),
            Opcion("Apariencia", "Modo claro, oscuro o igual que el teléfono", R.drawable.ic_qd_configuracion, null,
                R.color.qd_c_configuracion, accion = { (activity as? MainActivity)?.elegirTema() }),
            Opcion("Cerrar sesión", "Salir de la cuenta en este teléfono", R.drawable.ic_salida, null,
                R.color.rojo_error, accion = { (activity as? MainActivity)?.confirmarCerrarSesion() })
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val negocio = SessionManager(requireContext()).obtenerNombreNegocio()
        binding.tvSaludo.text = saludo() + (negocio?.let { ",\n$it" } ?: "")
        binding.tvFecha.text = Formato.fechaLegible(System.currentTimeMillis())
        // "Queso" blanco y "Data" dorado en un solo texto (así se achica junto si no cabe)
        binding.tvMarca.text = SpannableString("QuesoData").apply {
            setSpan(ForegroundColorSpan(color(R.color.dorado_titulo)), 5, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.cardStockBajo.setOnClickListener { ir(R.id.nav_inventario) }
        binding.datoLeche.setOnClickListener { ir(R.id.nav_leche) }
        binding.datoQueso.setOnClickListener { ir(R.id.nav_produccion) }
        binding.datoVentas.setOnClickListener { ir(R.id.nav_ventas) }
        binding.datoPagar.setOnClickListener { ir(R.id.nav_liquidaciones) }
        // Cabecera con solo las esquinas de abajo redondeadas (el rectángulo sube por encima del borde)
        binding.cabecera.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                val radio = dp(28).toFloat()
                outline.setRoundRect(0, -radio.toInt(), v.width, v.height, radio)
            }
        }
        binding.cabecera.clipToOutline = true
        binding.cardReportes.setOnClickListener { ir(R.id.nav_reportes) }
        binding.btnMenu.setOnClickListener { abrirConfiguracion() }

        construirMenu()
    }

    override fun onResume() {
        super.onResume()
        // Al volver de registrar leche, producción o ventas, el resumen se actualiza
        cargar()
    }

    private fun construirMenu() {
        llenar(binding.filaHoy, hoy, 2) { o, fila -> accionDiaria(o, fila, enInicio = true) }
        // En celulares angostos (< 360 dp) una tarjeta grande por fila: si no, el texto cae sobre la ilustración
        val columnas = if (resources.configuration.screenWidthDp < 360) 1 else 2
        llenar(binding.contenedorPrincipal, principales, columnas) { o, fila -> tarjetaGrande(o, fila) }
        llenar(binding.contenedorMas, masOpciones, 2) { o, fila -> tarjetaPequena(o, fila) }
    }

    /** Tarjeta crema de la guía de diseño: fondo [fondo] opaco, sombra suave y sin borde. */
    private fun tarjetaCrema(card: MaterialCardView, @ColorInt fondo: Int, alfa: Int = 0xFF) {
        card.setCardBackgroundColor(ColorUtils.setAlphaComponent(fondo, minOf(alfa, Color.alpha(fondo))))
        card.cardElevation = dp(3).toFloat()
        card.strokeWidth = 0
    }

    /** Configuración: Mi cuenta, Cambiar contraseña, Apariencia y Cerrar sesión, en una sola ventana. */
    private fun abrirConfiguracion() {
        val lista = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), 0)
        }
        val dialogo = AlertDialog.Builder(requireContext())
            .setTitle("Configuración")
            .setView(lista)
            .setNegativeButton("Cerrar", null)
            .create()
        configuracion.forEach { o ->
            val fila = accionDiaria(o, lista)
            fila.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(5), 0, dp(5)) }
            // Primero se cierra esta ventana y luego se abre la pantalla o la acción
            fila.setOnClickListener {
                dialogo.dismiss()
                val destino = o.destino
                if (destino != null) ir(destino) else o.accion?.invoke()
            }
            lista.addView(fila)
        }
        dialogo.show()
    }

    /** Pone las tarjetas en filas de [porFila]. */
    private fun llenar(contenedor: LinearLayout, opciones: List<Opcion>, porFila: Int, crear: (Opcion, ViewGroup) -> View) {
        contenedor.removeAllViews()
        opciones.chunked(porFila).forEach { grupo ->
            val fila = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            grupo.forEach { fila.addView(crear(it, fila)) }
            contenedor.addView(fila)
        }
    }

    /** [enInicio] = tarjeta de Inicio (en la ventana de Configuración va normal). */
    private fun accionDiaria(o: Opcion, padre: ViewGroup, enInicio: Boolean = false): View {
        val t = ItemAccionDiariaBinding.inflate(layoutInflater, padre, false)
        if (enInicio) tarjetaCrema(t.root, color(R.color.superficie))
        t.ivIcono.setImageResource(o.icono)
        t.ivIcono.background.mutate().setTint(color(o.circulo))
        t.tvTitulo.text = o.titulo
        t.tvDescripcion.text = o.descripcion
        alTocar(t.root, o)
        return t.root
    }

    private fun tarjetaGrande(o: Opcion, padre: ViewGroup): View {
        val t = ItemTarjetaGrandeBinding.inflate(layoutInflater, padre, false)
        if (o.fondoDegradado != null) t.fondo.setBackgroundResource(o.fondoDegradado)
        else tarjetaCrema(t.root, color(o.fondo))
        t.ivIcono.setImageResource(o.icono)
        t.ivIcono.background.mutate().setTint(color(o.circulo))
        t.ivFlecha.background.mutate().setTint(color(o.circulo))
        t.tvTitulo.text = o.titulo
        t.tvDescripcion.text = o.descripcion
        if (o.claro) {
            // Tarjeta verde: textos blancos, círculos translúcidos y flecha blanca con ícono verde
            t.tvTitulo.setTextColor(color(R.color.white))
            t.tvDescripcion.setTextColor(0xD9FFFFFF.toInt())
            t.ivIcono.background.setTint(0x2EFFFFFF)
            t.ivFlecha.background.setTint(color(R.color.white))
            t.ivFlecha.imageTintList = ColorStateList.valueOf(color(o.circulo))
        }
        ponerIlustracion(t.ivIlustracion, o)
        alTocar(t.root, o)
        return t.root
    }

    private fun tarjetaPequena(o: Opcion, padre: ViewGroup): View {
        val t = ItemTarjetaPequenaBinding.inflate(layoutInflater, padre, false)
        tarjetaCrema(t.root, color(o.fondo))
        t.ivIcono.setImageResource(o.icono)
        t.ivIcono.background.mutate().setTint(color(o.circulo))
        t.tvTitulo.text = o.titulo
        ponerIlustracion(t.ivIlustracion, o)
        alTocar(t.root, o)
        return t.root
    }

    /** Ilustración en la esquina inferior derecha, sin margen, con el alto del diseño. */
    private fun ponerIlustracion(iv: ImageView, o: Opcion) {
        val img = o.ilustracion ?: return
        iv.setImageResource(img)
        iv.layoutParams = iv.layoutParams.apply { height = dp(o.alto) }
    }

    private fun alTocar(v: View, o: Opcion) {
        v.contentDescription = o.titulo
        val destino = o.destino
        when {
            destino != null -> v.setOnClickListener { ir(destino, o.args?.let { Bundle(it) }) }
            o.accion != null -> v.setOnClickListener { o.accion.invoke() }
            else -> {
                // Solo diseño: sin pantalla todavía
                v.isClickable = false
                v.foreground = null
            }
        }
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(requireContext(), id)

    private fun ir(destino: Int, args: Bundle? = null) {
        val opciones = NavOptions.Builder().setLaunchSingleTop(true).build()
        findNavController().navigate(destino, args, opciones)
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.getResumenHoy(Formato.hoyIso()) }, binding.swipeRefresh, alFallar = { r ->
            binding.tvError.text = "No se pudo cargar el resumen: ${r.mensaje}\nDesliza hacia abajo para reintentar."
            binding.tvError.visibility = View.VISIBLE
        }) {
            binding.tvError.visibility = View.GONE
            mostrarResumen(it ?: ResumenHoy())
        }
        conApi({ RetrofitClient.api.getProductos() }) { productos ->
            val bajos = productos.orEmpty().filter { it.stockBajo }
            binding.cardStockBajo.visibility = if (bajos.isEmpty()) View.GONE else View.VISIBLE
            binding.tvStockBajo.text = bajos.joinToString("\n") {
                "${it.nombre}: ${Formato.kg(it.stockKg)} (mínimo ${Formato.kg(it.stockMinimoKg)})"
            }
        }
        // Merma: stock estimado de la semana y, al cierre, la única pregunta (si sobró queso)
        conApi({ RetrofitClient.api.getMerma() }) { m ->
            val actual = m?.actual
            stockEstimado = actual?.takeIf { it.kgProducidos + it.stockInicialKg > 0 }?.stockEstimadoKg
            mostrarDetalleQueso()
            val pendiente = m?.pendiente
            if (pendiente != null && !cierrePreguntado) {
                cierrePreguntado = true
                preguntarCierre(pendiente, m.promedioPct) { cargar() }
            }
        }
        // Lo que se debe a los ganaderos: semana en curso + semanas cerradas sin pagar
        conApi({ RetrofitClient.api.getSemanasLiquidacion(n = 12) }) { lista ->
            val semanas = lista.orEmpty()
            val total = semanas.sumOf { it.totalPendiente }
            val actual = semanas.firstOrNull()?.totalPendiente ?: 0.0
            val faltaPrecio = semanas.any { it.sinPrecio > 0 }
            // Sin precio del litro no se sabe cuánto se debe: se dice eso en vez de mostrar $ 0
            binding.tvPagar.text = if (faltaPrecio && total <= 0) "Falta precio" else Formato.moneda(total)
            binding.tvPagarDetalle.text = when {
                faltaPrecio && total <= 0 -> "toca para definir el litro"
                faltaPrecio -> "y una semana sin precio"
                total <= 0 -> "todo pagado"
                total - actual > 0.5 -> "${Formato.moneda(total - actual)} de semanas cerradas"
                else -> "semana en curso"
            }
        }
    }

    private fun mostrarResumen(r: ResumenHoy) {
        binding.tvLeche.text = Formato.litros(r.litrosHoy)
        binding.tvLecheDetalle.text = "${Formato.numero(r.litrosHoy / LecheFragment.LITROS_POR_LATA, 1)} latas"
        binding.tvQueso.text = Formato.kg(r.kgProducidosHoy)
        kgHoy = r.kgProducidosHoy
        mostrarDetalleQueso()
        binding.tvVentas.text = Formato.moneda(r.totalVentasHoy)
        binding.tvVentasDetalle.text = "${r.numeroVentasHoy} ${if (r.numeroVentasHoy == 1) "venta" else "ventas"}"
    }

    private fun mostrarDetalleQueso() {
        binding.tvQuesoDetalle.text = stockEstimado?.let { "stock estimado ${Formato.kg(it)}" }
            ?: "${Formato.numero(Formato.kgALibras(kgHoy))} lb"
    }

    private fun saludo(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Argumento para abrir directamente el formulario de "nuevo" (leche, producción, venta). */
        const val ARG_NUEVO = "abrirNuevo"

        /** El cierre semanal se pregunta una sola vez por sesión (si se cancela, vuelve a salir al abrir la app). */
        private var cierrePreguntado = false
    }
}
