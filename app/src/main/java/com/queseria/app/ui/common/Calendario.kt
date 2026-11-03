package com.queseria.app.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.queseria.app.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val MESES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)
private val DIAS_SEMANA = listOf("D", "L", "M", "M", "J", "V", "S")
private val DIAS_LARGOS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/**
 * Calendario de la guía de diseño: arriba la fecha elegida ("2026 / Lun, 2 de nov"), el mes en una
 * barra verde con flechas, la fila D L M M J V S y los días como botones crema (el elegido en verde,
 * hoy con borde). Abajo Cancelar y Aceptar. [alElegir] recibe la fecha al tocar Aceptar.
 */
fun mostrarCalendario(ctx: Context, inicial: LocalDate, alElegir: (LocalDate) -> Unit) {
    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun color(id: Int) = ContextCompat.getColor(ctx, id)
    val negrita = ResourcesCompat.getFont(ctx, R.font.poppins_bold)
    val semi = ResourcesCompat.getFont(ctx, R.font.poppins_semibold)
    fun fondo(colorId: Int, radio: Int, borde: Int? = null) = GradientDrawable().apply {
        setColor(color(colorId))
        cornerRadius = dp(radio).toFloat()
        borde?.let { setStroke(dp(2), color(it)) }
    }
    fun texto(sp: Float, colorId: Int, fuente: Typeface?) = TextView(ctx).apply {
        textSize = sp
        setTextColor(color(colorId))
        typeface = fuente
        includeFontPadding = false
    }

    var elegida = inicial
    var mes = YearMonth.from(inicial)
    val hoy = LocalDate.now()

    val raiz = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = fondo(R.color.beige_fondo, 28)
        setPadding(dp(16), dp(18), dp(16), dp(16))
    }

    // Fecha elegida, grande
    val tvAnio = texto(14f, R.color.gris_texto_secundario, semi)
    val tvFecha = texto(24f, R.color.verde_oscuro, negrita).apply { setPadding(0, dp(4), 0, 0) }
    raiz.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = fondo(R.color.superficie, 20)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        addView(tvAnio)
        addView(tvFecha)
    })

    // Mes con flechas sobre verde, y la fila de los días de la semana en verde oscuro
    val tvMes = texto(17f, R.color.white, semi).apply { gravity = Gravity.CENTER }
    fun flecha(icono: Int, descripcion: String) = ImageButton(ctx).apply {
        setImageResource(icono)
        imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.white))
        background = null
        contentDescription = descripcion
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }
    val anterior = flecha(R.drawable.ic_flecha_izquierda, "Mes anterior")
    val siguiente = flecha(R.drawable.ic_flecha_derecha, "Mes siguiente")
    val tarjetaMes = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = fondo(R.color.qd_venta_inicio, 20)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(14) }
    }
    tarjetaMes.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = fondo(R.color.verde_principal, 20)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        addView(anterior)
        addView(tvMes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(siguiente)
    })
    tarjetaMes.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(6), dp(10), dp(6), dp(10))
        DIAS_SEMANA.forEach { d ->
            addView(texto(14f, R.color.white, semi).apply { text = d; gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    })
    raiz.addView(tarjetaMes)

    val cuadricula = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = fondo(R.color.superficie, 20)
        setPadding(dp(6), dp(8), dp(6), dp(8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) }
    }
    raiz.addView(cuadricula)

    fun dibujar() {
        tvAnio.text = elegida.year.toString()
        tvFecha.text = "${DIAS_LARGOS[elegida.dayOfWeek.value - 1]}, ${elegida.dayOfMonth} de ${MESES[elegida.monthValue - 1].lowercase().take(3)}"
        tvMes.text = "${MESES[mes.monthValue - 1]} de ${mes.year}"
        cuadricula.removeAllViews()
        // Columna del primer día: domingo = 0
        val desfase = mes.atDay(1).dayOfWeek.let { if (it == DayOfWeek.SUNDAY) 0 else it.value }
        val celdas = desfase + mes.lengthOfMonth()
        for (fila in 0 until (celdas + 6) / 7) {
            val renglon = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 7) {
                val n = fila * 7 + col - desfase + 1
                val celda = texto(16f, R.color.gris_texto_principal, negrita).apply { gravity = Gravity.CENTER }
                if (n in 1..mes.lengthOfMonth()) {
                    val dia = mes.atDay(n)
                    celda.text = n.toString()
                    when {
                        dia == elegida -> { celda.background = fondo(R.color.verde_principal, 14); celda.setTextColor(color(R.color.white)) }
                        dia == hoy -> celda.background = fondo(R.color.crema, 14, R.color.verde_claro)
                        else -> celda.background = fondo(R.color.crema, 14)
                    }
                    celda.contentDescription = "$n de ${MESES[mes.monthValue - 1]}"
                    celda.setOnClickListener { elegida = dia; dibujar() }
                }
                renglon.addView(celda, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            cuadricula.addView(renglon)
        }
    }
    anterior.setOnClickListener { mes = mes.minusMonths(1); dibujar() }
    siguiente.setOnClickListener { mes = mes.plusMonths(1); dibujar() }
    dibujar()

    val dialogo = Dialog(ctx, R.style.Theme_QuesoData_Calendario)
    // Cancelar y Aceptar
    val botones = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(16), 0, 0)
    }
    val cancelar = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = "Cancelar"
        isAllCaps = false
        setTextColor(color(R.color.verde_oscuro))
        setIconResource(R.drawable.ic_cerrar)
        iconTint = android.content.res.ColorStateList.valueOf(color(R.color.verde_oscuro))
        strokeColor = android.content.res.ColorStateList.valueOf(color(R.color.verde_claro))
        cornerRadius = dp(20)
        setOnClickListener { dialogo.dismiss() }
    }
    val aceptar = MaterialButton(ctx).apply {
        text = "Aceptar"
        isAllCaps = false
        setIconResource(R.drawable.ic_listo)
        cornerRadius = dp(20)
        setOnClickListener { dialogo.dismiss(); alElegir(elegida) }
    }
    botones.addView(cancelar, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(10) })
    botones.addView(aceptar, LinearLayout.LayoutParams(0, dp(54), 1f))
    raiz.addView(botones)

    dialogo.setContentView(raiz)
    dialogo.window?.setLayout((ctx.resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    dialogo.show()
}
