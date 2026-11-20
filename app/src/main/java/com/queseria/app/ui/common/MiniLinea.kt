package com.queseria.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Curva pequeña (sin ejes ni textos) con brillo y un punto al final, para las tarjetas de Ganancias
 * y Reportes. Se dibuja con los datos reales (por ejemplo, las últimas 8 semanas): no es decoración.
 * Barata de dibujar: un Path, dos trazos y un degradado; sin desenfoque.
 */
class MiniLinea @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var valores: List<Double> = emptyList()
    private var color = 0xFF38E26A.toInt()
    private val densidad = resources.displayMetrics.density

    private val pBrillo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val pLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pPunto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linea = Path()
    private val relleno = Path()

    /** [valores] de la más antigua a la más reciente; [color] de la curva. */
    fun mostrar(valores: List<Double>, color: Int) {
        this.valores = valores
        this.color = color
        contentDescription = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (valores.size < 2) return
        val margen = 6 * densidad
        val ancho = width - 2 * margen
        val alto = height - 2 * margen
        val min = valores.min()
        val max = valores.max()
        val rango = (max - min).takeIf { it > 0 } ?: 1.0
        val puntos = valores.mapIndexed { i, v ->
            val x = margen + ancho * i / (valores.size - 1)
            val y = margen + alto * (1 - ((v - min) / rango)).toFloat()
            x to y
        }
        // Curva suave (cúbica entre puntos)
        linea.reset()
        linea.moveTo(puntos[0].first, puntos[0].second)
        for (i in 1 until puntos.size) {
            val (x0, y0) = puntos[i - 1]
            val (x1, y1) = puntos[i]
            val medio = (x0 + x1) / 2
            linea.cubicTo(medio, y0, medio, y1, x1, y1)
        }
        relleno.reset()
        relleno.addPath(linea)
        relleno.lineTo(puntos.last().first, height.toFloat())
        relleno.lineTo(puntos.first().first, height.toFloat())
        relleno.close()
        pRelleno.shader = LinearGradient(0f, margen, 0f, height.toFloat(),
            ColorUtils.setAlphaComponent(color, 0x55), ColorUtils.setAlphaComponent(color, 0x00), Shader.TileMode.CLAMP)
        canvas.drawPath(relleno, pRelleno)

        pBrillo.color = ColorUtils.setAlphaComponent(color, 0x44)
        pBrillo.strokeWidth = 6 * densidad
        canvas.drawPath(linea, pBrillo)
        pLinea.color = color
        pLinea.strokeWidth = 2.2f * densidad
        canvas.drawPath(linea, pLinea)

        val (xf, yf) = puntos.last()
        pPunto.color = ColorUtils.setAlphaComponent(color, 0x55)
        canvas.drawCircle(xf, yf, 6 * densidad, pPunto)
        pPunto.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(xf, yf, 3 * densidad, pPunto)
    }
}
