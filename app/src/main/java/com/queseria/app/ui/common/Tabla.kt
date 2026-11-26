package com.queseria.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import com.queseria.app.R

/**
 * Tabla tipo hoja de cálculo para las listas de una semana (Leche y Ventas):
 * - Encabezado verde en negrilla que no se mueve al bajar (va fuera de la lista: [crearEncabezado]).
 * - Rejilla fina, filas alternas, números a la derecha (con separador de miles, ya formateados).
 * - Primera columna fija; las demás se desplazan de lado, todas a la vez (encabezado incluido).
 * - Al final, la fila de totales resaltada. Tocar una fila = [alTocar] (abrirla). Borrar no se hace desde la
 *   lista (ni manteniendo presionado): va aparte, en rojo, dentro del registro.
 * - Filas marcadas: hechas sin internet ([Marca.PENDIENTE]), rechazadas ([Marca.RECHAZADA]) o con algo
 *   que falta ([Marca.AVISO]); la [nota] sale debajo del dato de la primera columna.
 * Todo se arma con vistas simples (sin XML ni sombras) para que se mueva fluido en teléfonos sencillos.
 */
class Tabla<T>(
    private val columnas: List<Columna>,
    private val celdas: (T) -> List<String>,
    private val marca: (T) -> Marca = { Marca.NINGUNA },
    private val nota: (T) -> String? = { null },
    private val alTocar: ((T) -> Unit)? = null,
    /** Color de una celda (columna i) según el dato: estados, negativos… null = el normal. */
    private val colorCelda: (T, Int) -> Int? = { _, _ -> null },
    /** Colores de la tabla: [Paleta.CLARA] (sigue el modo claro/oscuro) o [Paleta.OSCURA] (Reportes). */
    private val paleta: Paleta = Paleta.CLARA
) : RecyclerView.Adapter<Tabla.Holder>() {

    /** Recursos de color de la tabla. */
    data class Paleta(
        val encabezado: Int, val textoEncabezado: Int, val rejilla: Int, val par: Int, val impar: Int,
        val total: Int, val texto: Int, val textoTotal: Int, val textoVacio: Int,
        val pendiente: Int, val rechazada: Int
    ) {
        companion object {
            val CLARA = Paleta(R.color.tabla_encabezado, R.color.white, R.color.tabla_rejilla, R.color.tabla_fila_par,
                R.color.tabla_fila_impar, R.color.tabla_total, R.color.gris_texto_principal, R.color.verde_oscuro,
                R.color.gris_texto_secundario, R.color.tabla_pendiente, R.color.tabla_rechazada)
            /** Para las pantallas oscuras con neón (Reportes): igual en modo claro y oscuro. */
            val OSCURA = Paleta(R.color.tabla_os_encabezado, R.color.gr_texto, R.color.tabla_os_rejilla, R.color.tabla_os_par,
                R.color.tabla_os_impar, R.color.tabla_os_total, R.color.gr_texto, R.color.gr_neon,
                R.color.gr_texto_2, R.color.tabla_os_pendiente, R.color.tabla_os_rechazada)
        }
    }

    /** [ancho] en dp; [numero] = alineada a la derecha. La primera columna es la fija. */
    data class Columna(val titulo: String, val ancho: Int, val numero: Boolean = false)

    enum class Marca { NINGUNA, PENDIENTE, RECHAZADA, AVISO }

    private var filas: List<T> = emptyList()
    private var totales: List<String>? = null
    private var vacio: String = ""

    // Desplazamiento lateral compartido por el encabezado y todas las filas
    private var desplazamiento = 0
    private var sincronizando = false
    private val desplazables = mutableSetOf<HorizontalScrollView>()

    /** [totales]: una celda por columna (null = sin fila de totales). [vacio]: texto si no hay filas. */
    fun mostrar(filas: List<T>, totales: List<String>?, vacio: String) {
        this.filas = filas
        this.totales = totales
        this.vacio = vacio
        notifyDataSetChanged()
    }

    /** Pone el encabezado (fijo) dentro de [contenedor]. */
    fun crearEncabezado(contenedor: ViewGroup) {
        contenedor.removeAllViews()
        val ctx = contenedor.context
        val fila = armarFila(ctx)
        fila.celdas.forEachIndexed { i, tv ->
            tv.text = columnas[i].titulo
            tv.setTypeface(negrita(ctx), Typeface.NORMAL)
            tv.setTextColor(ContextCompat.getColor(ctx, paleta.textoEncabezado))
            tv.setBackgroundColor(ContextCompat.getColor(ctx, paleta.encabezado))
            tv.minHeight = dp(ctx, 42)
        }
        fila.raiz.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        contenedor.addView(fila.raiz)
        contenedor.visibility = View.VISIBLE
        registrar(fila.desplazable)
    }

    override fun getItemCount() = if (filas.isEmpty()) 1 + (if (totales != null) 1 else 0) else filas.size + (if (totales != null) 1 else 0)

    override fun getItemViewType(position: Int) = when {
        filas.isEmpty() -> if (position == 0) TIPO_VACIO else TIPO_TOTAL
        position == filas.size -> TIPO_TOTAL
        else -> TIPO_FILA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        if (viewType == TIPO_VACIO) {
            val tv = TextView(ctx).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(ctx, 16), dp(ctx, 22), dp(ctx, 16), dp(ctx, 22))
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(ctx, paleta.textoVacio))
                setBackgroundColor(ContextCompat.getColor(ctx, paleta.par))
                textSize = 14f
            }
            return Holder(tv, null)
        }
        val fila = armarFila(ctx)
        fila.raiz.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        registrar(fila.desplazable)
        return Holder(fila.raiz, fila)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val fila = holder.fila
        if (fila == null) {
            (holder.itemView as TextView).text = vacio
            return
        }
        pintar(fila, position, getItemViewType(position) == TIPO_TOTAL)
    }

    /**
     * Tabla corta dentro de una página (por ejemplo, los descuentos del detalle de una liquidación):
     * encabezado, filas y totales como vistas normales en [contenedor], sin lista que recicle.
     */
    fun llenarEn(contenedor: LinearLayout, filas: List<T>, totales: List<String>?, vacio: String) {
        this.filas = filas
        this.totales = totales
        this.vacio = vacio
        contenedor.removeAllViews()
        desplazables.clear()
        val ctx = contenedor.context
        val encabezado = FrameLayout(ctx)
        crearEncabezado(encabezado)
        contenedor.addView(encabezado)
        for (i in 0 until itemCount) {
            if (getItemViewType(i) == TIPO_VACIO) {
                val h = onCreateViewHolder(contenedor, TIPO_VACIO)
                onBindViewHolder(h, i)
                h.itemView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                contenedor.addView(h.itemView)
                continue
            }
            val fila = armarFila(ctx)
            registrar(fila.desplazable)
            pintar(fila, i, getItemViewType(i) == TIPO_TOTAL)
            contenedor.addView(fila.raiz, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun pintar(fila: Fila, position: Int, esTotal: Boolean) {
        val ctx = fila.raiz.context
        val elemento = if (esTotal) null else filas[position]
        val textos = if (esTotal) totales.orEmpty() else celdas(elemento!!)
        val m = elemento?.let(marca) ?: Marca.NINGUNA
        val fondo = ContextCompat.getColor(ctx, when {
            esTotal -> paleta.total
            m == Marca.RECHAZADA -> paleta.rechazada
            m == Marca.PENDIENTE || m == Marca.AVISO -> paleta.pendiente
            position % 2 == 0 -> paleta.par
            else -> paleta.impar
        })
        val colorTexto = ContextCompat.getColor(ctx, if (esTotal) paleta.textoTotal else paleta.texto)
        fila.celdas.forEachIndexed { i, tv ->
            tv.setBackgroundColor(fondo)
            tv.setTextColor(colorTexto)
            tv.setTypeface(if (esTotal || i == 0) negrita(ctx) else normal(ctx), Typeface.NORMAL)
            tv.text = textos.getOrElse(i) { "" }
            elemento?.let { e -> colorCelda(e, i) }?.let { tv.setTextColor(ContextCompat.getColor(ctx, it)) }
        }
        // Debajo de la fecha: "Pendiente", "No se subió" o lo que falte
        val n = elemento?.let(nota)
        if (n != null) {
            val color = ContextCompat.getColor(ctx, if (m == Marca.RECHAZADA) R.color.rojo_error else R.color.ambar_advertencia)
            val primera = fila.celdas[0]
            primera.text = android.text.SpannableStringBuilder(textos.getOrElse(0) { "" }).append("\n").apply {
                val inicio = length
                append(n)
                setSpan(android.text.style.ForegroundColorSpan(color), inicio, length, 0)
                setSpan(android.text.style.RelativeSizeSpan(0.78f), inicio, length, 0)
            }
        }
        fila.raiz.contentDescription = if (esTotal) "Total: " + columnas.indices.drop(1).joinToString(", ") { "${columnas[it].titulo} ${textos.getOrElse(it) { "" }}" }
            else columnas.indices.joinToString(", ") { "${columnas[it].titulo} ${textos.getOrElse(it) { "" }}" } + (n?.let { ", $it" } ?: "")
        // Tocar = abrir (para corregir). Borrar NO se hace desde la lista: va aparte, dentro del registro.
        val accion = alTocar
        val tocar = if (elemento == null || accion == null) null else View.OnClickListener { accion(elemento) }
        listOf(fila.celdas[0], fila.contenido).forEach {
            it.setOnClickListener(tocar)
            it.setOnLongClickListener(null)
            it.isClickable = tocar != null
            it.isLongClickable = false
        }
        // La fila nueva o reciclada queda corrida igual que las demás
        fila.desplazable.doOnLayout { mover(fila.desplazable, desplazamiento) }
    }

    class Holder(vista: View, val fila: Fila?) : RecyclerView.ViewHolder(vista)

    class Fila(
        val raiz: LinearLayout,
        val desplazable: HorizontalScrollView,
        val contenido: LinearLayout,
        val celdas: List<TextView>
    )

    /** Fila: celda fija + (desplazable → celdas). La rejilla es el fondo que asoma entre celdas. */
    private fun armarFila(ctx: Context): Fila {
        val rejilla = ContextCompat.getColor(ctx, paleta.rejilla)
        val linea = dp(ctx, 1).coerceAtLeast(1)
        val raiz = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(rejilla)
        }
        val celdas = columnas.mapIndexed { i, c ->
            TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, c.ancho), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    // Línea más gruesa a la derecha de la columna fija
                    marginEnd = if (i == 0) linea * 2 else linea
                    bottomMargin = linea
                }
                minHeight = dp(ctx, 46)
                setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                gravity = Gravity.CENTER_VERTICAL or if (c.numero) Gravity.END else Gravity.START
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }
        }
        raiz.addView(celdas[0])
        val contenido = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            celdas.drop(1).forEach { addView(it) }
        }
        val desplazable = HorizontalScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(contenido)
        }
        raiz.addView(desplazable)
        celdas[0].foreground = ContextCompat.getDrawable(ctx, R.drawable.bg_tabla_toque)
        contenido.foreground = ContextCompat.getDrawable(ctx, R.drawable.bg_tabla_toque)
        return Fila(raiz, desplazable, contenido, celdas)
    }

    private fun registrar(h: HorizontalScrollView) {
        desplazables += h
        h.setOnScrollChangeListener { v, x, _, _, _ ->
            if (sincronizando) return@setOnScrollChangeListener
            desplazamiento = x
            sincronizando = true
            desplazables.forEach { if (it !== v) it.scrollTo(x, 0) }
            sincronizando = false
        }
    }

    private fun mover(h: HorizontalScrollView, x: Int) {
        sincronizando = true
        h.scrollTo(x, 0)
        sincronizando = false
    }

    private fun negrita(ctx: Context) = ResourcesCompat.getFont(ctx, R.font.poppins_semibold)
    private fun normal(ctx: Context) = ResourcesCompat.getFont(ctx, R.font.poppins_regular)

    private companion object {
        const val TIPO_FILA = 0
        const val TIPO_TOTAL = 1
        const val TIPO_VACIO = 2
        fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    }
}

/** "vie 20" (la tabla es de una sola semana: el mes sobra). */
fun fechaCorta(fecha: String?): String {
    val f = runCatching { java.time.LocalDate.parse(com.queseria.app.util.Formato.soloFecha(fecha)) }.getOrNull() ?: return ""
    return "${com.queseria.app.util.Semana.nombreDia(f)} ${f.dayOfMonth}"
}

/** "10 lb" o "2,5 kg". */
fun cantidadCorta(cantidad: Double, unidad: String): String {
    val lb = unidad.equals("lb", ignoreCase = true) || unidad.equals("libras", ignoreCase = true)
    return "${com.queseria.app.util.Formato.numero(cantidad)} ${if (lb) "lb" else "kg"}"
}

/** Marca de la fila según el registro hecho sin internet (null = ya está en el servidor). */
fun marcaPendiente(p: com.queseria.app.offline.Pendiente?): Tabla.Marca = when {
    p == null -> Tabla.Marca.NINGUNA
    p.rechazado -> Tabla.Marca.RECHAZADA
    else -> Tabla.Marca.PENDIENTE
}

fun androidx.fragment.app.Fragment.dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()
