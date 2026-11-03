package com.queseria.app.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.queseria.app.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Gráfico simple dibujado a mano (sin librerías), con la paleta de QuesoData.
 * - [Tipo.BARRAS]: barras agrupadas por etiqueta (una barra por serie). Admite negativos.
 * - [Tipo.LINEA]: una línea con área degradada (usa solo la primera serie).
 * Se llena con [mostrar].
 */
class Grafico @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Tipo { BARRAS, LINEA }

    data class Serie(val nombre: String, val valores: List<Double>, val color: Int)

    private var tipo = Tipo.BARRAS
    private var etiquetas: List<String> = emptyList()
    private var series: List<Serie> = emptyList()
    private var formatoValor: (Double) -> String = { it.toString() }
    /** Índice que se resalta (por ejemplo, la semana actual). */
    private var resaltar: Int = -1
    private var progreso = 1f

    private val densidad = resources.displayMetrics.density
    private fun dp(v: Float) = v * densidad

    private val fuente = ResourcesCompat.getFont(context, R.font.poppins_regular)
    private val fuenteFuerte = ResourcesCompat.getFont(context, R.font.poppins_semibold)

    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGuia = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gris_borde)
        strokeWidth = dp(1f)
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val pBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.crema_oscura)
        strokeWidth = dp(1.5f)
    }
    private val pTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gris_texto_secundario)
        textSize = dp(10.5f)
        typeface = fuente
        textAlign = Paint.Align.CENTER
    }
    private val pValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.verde_oscuro)
        textSize = dp(10f)
        typeface = fuenteFuerte
        textAlign = Paint.Align.CENTER
    }
    private val pLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pPunto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    fun mostrar(
        tipo: Tipo,
        etiquetas: List<String>,
        series: List<Serie>,
        resaltar: Int = -1,
        formatoValor: (Double) -> String = { it.toString() }
    ) {
        this.tipo = tipo
        this.etiquetas = etiquetas
        this.series = series
        this.resaltar = resaltar
        this.formatoValor = formatoValor
        contentDescription = series.joinToString(". ") { s ->
            s.nombre + ": " + etiquetas.zip(s.valores).joinToString { (e, v) -> "$e ${formatoValor(v)}" }
        }
        // Animación de entrada: las barras crecen / la línea se dibuja
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { progreso = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val alto = resolveSize(dp(190f).toInt(), heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), alto)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = etiquetas.size
        if (n == 0 || series.isEmpty()) return

        val izq = paddingLeft.toFloat()
        val der = width - paddingRight.toFloat()
        val arriba = paddingTop + dp(18f)            // espacio para el valor sobre la barra
        val abajo = height - paddingBottom - dp(22f) // espacio para las etiquetas

        val todos = series.flatMap { it.valores }
        val maximo = max(todos.maxOrNull() ?: 0.0, 0.0)
        val minimo = min(todos.minOrNull() ?: 0.0, 0.0)
        val rango = (maximo - minimo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (abajo - ((v - minimo) / rango) * (abajo - arriba)).toFloat()
        val cero = y(0.0)

        // Líneas guía
        for (i in 1..3) {
            val gy = arriba + (abajo - arriba) * i / 4f
            canvas.drawLine(izq, gy, der, gy, pGuia)
        }
        canvas.drawLine(izq, cero, der, cero, pBase)

        val ancho = (der - izq) / n
        etiquetas.forEachIndexed { i, e ->
            pTexto.typeface = if (i == resaltar) fuenteFuerte else fuente
            pTexto.color = ContextCompat.getColor(context, if (i == resaltar) R.color.verde_oscuro else R.color.gris_texto_secundario)
            canvas.drawText(e, izq + ancho * (i + 0.5f), height - paddingBottom - dp(6f), pTexto)
        }

        if (tipo == Tipo.BARRAS) dibujarBarras(canvas, izq, ancho, cero, ::y) else dibujarLinea(canvas, izq, ancho, abajo, ::y)
    }

    private fun dibujarBarras(canvas: Canvas, izq: Float, ancho: Float, cero: Float, y: (Double) -> Float) {
        val grupo = ancho * 0.62f
        val barra = grupo / series.size
        val radio = min(dp(6f), barra / 2)
        for (i in etiquetas.indices) {
            val x0 = izq + ancho * i + (ancho - grupo) / 2
            series.forEachIndexed { s, serie ->
                val v = serie.valores.getOrNull(i) ?: return@forEachIndexed
                val alto = (y(v) - cero) * progreso
                val l = x0 + barra * s + dp(1.5f)
                val r = x0 + barra * (s + 1) - dp(1.5f)
                rect.set(l, min(cero, cero + alto), r, max(cero, cero + alto))
                val base = if (v < 0) ContextCompat.getColor(context, R.color.rojo_error) else serie.color
                // Las semanas que no se resaltan se ven un poco más suaves
                pRelleno.color = if (resaltar >= 0 && i != resaltar) ColorUtils.setAlphaComponent(base, 0xB0) else base
                canvas.drawRoundRect(rect, radio, radio, pRelleno)
                // Se escribe el valor solo en la barra resaltada (o en todas si hay pocas), para no saturar
                if (progreso == 1f && v != 0.0 && (i == resaltar || etiquetas.size <= 4) && series.size == 1) {
                    // Positivo: encima de la barra. Negativo: arriba de la línea del cero (no choca con las etiquetas)
                    val ty = if (v >= 0) rect.top - dp(5f) else cero - dp(5f)
                    pValor.textAlign = if (i == etiquetas.lastIndex) Paint.Align.RIGHT else Paint.Align.CENTER
                    val tx = if (i == etiquetas.lastIndex) r else (l + r) / 2
                    pValor.color = ContextCompat.getColor(context, if (v < 0) R.color.rojo_error else R.color.verde_oscuro)
                    canvas.drawText(formatoValor(v), tx, ty, pValor)
                    pValor.textAlign = Paint.Align.CENTER
                    pValor.color = ContextCompat.getColor(context, R.color.verde_oscuro)
                }
            }
        }
    }

    private fun dibujarLinea(canvas: Canvas, izq: Float, ancho: Float, abajo: Float, y: (Double) -> Float) {
        val serie = series.first()
        // En la línea, un 0 significa "sin datos esa semana": ese punto no se dibuja
        val indices = serie.valores.indices.filter { serie.valores[it] != 0.0 }
        val puntos = indices.map { i -> (izq + ancho * (i + 0.5f)) to y(serie.valores[i]) }
        if (puntos.isEmpty()) {
            pTexto.color = ContextCompat.getColor(context, R.color.gris_texto_secundario)
            canvas.drawText("Sin producciones en estas semanas", width / 2f, abajo / 2, pTexto)
            return
        }
        val visibles = max(1, (puntos.size * progreso).toInt().coerceAtLeast(1))
        val tramo = puntos.take(visibles)

        // Área degradada bajo la línea
        path.reset()
        path.moveTo(tramo.first().first, abajo)
        tramo.forEach { (x, yy) -> path.lineTo(x, yy) }
        path.lineTo(tramo.last().first, abajo)
        path.close()
        pRelleno.shader = LinearGradient(
            0f, paddingTop.toFloat(), 0f, abajo,
            ColorUtils.setAlphaComponent(serie.color, 0x55), ColorUtils.setAlphaComponent(serie.color, 0x05),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, pRelleno)
        pRelleno.shader = null

        path.reset()
        tramo.forEachIndexed { i, (x, yy) -> if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy) }
        pLinea.color = serie.color
        canvas.drawPath(path, pLinea)

        tramo.forEachIndexed { j, (x, yy) ->
            val i = indices[j]
            pPunto.color = ContextCompat.getColor(context, R.color.white)
            canvas.drawCircle(x, yy, dp(5f), pPunto)
            pPunto.color = serie.color
            canvas.drawCircle(x, yy, dp(if (i == resaltar) 4.5f else 3f), pPunto)
            val v = serie.valores[i]
            if (progreso == 1f && v != 0.0 && (i == resaltar || j == tramo.lastIndex || abs(v - (serie.valores.maxOrNull() ?: 0.0)) < 1e-9)) {
                canvas.drawText(formatoValor(v), x, yy - dp(9f), pValor)
            }
        }
    }
}
