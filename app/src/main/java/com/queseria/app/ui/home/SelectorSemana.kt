package com.queseria.app.ui.home

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.databinding.DialogLecheBinding
import com.queseria.app.models.DiaLeche
import com.queseria.app.ui.common.conApi
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate

/**
 * Selector de fecha del formulario de leche, organizado por semanas de viernes a jueves:
 * arriba "Semana 44: vie 23 oct – jue 29 oct" con flechas, y debajo los 7 días como botones
 * con los litros ya registrados ese día (de todos los ganaderos). No deja elegir días futuros.
 *
 * [alCambiar] se llama cada vez que cambia el día elegido o llegan los litros de la semana.
 */
class SelectorSemana(
    private val fragment: Fragment,
    private val f: DialogLecheBinding,
    inicial: LocalDate,
    private val alCambiar: () -> Unit
) {
    var dia: LocalDate = inicial
        private set
    private var semana = Semana.de(inicial)
    private var litrosDe: Map<String, DiaLeche> = emptyMap()

    /** Litros ya registrados el día elegido (null mientras cargan). */
    val datosDelDia: DiaLeche? get() = litrosDe[dia.toString()]

    private val ctx get() = fragment.requireContext()

    init {
        f.btnSemanaAnterior.setOnClickListener { irA(semana.anterior()) }
        f.btnSemanaSiguiente.setOnClickListener { irA(semana.siguiente()) }
        // Sin avisar aquí: quien crea el selector todavía no tiene la referencia (la asignación
        // termina al salir del constructor). Avisa cuando lleguen los litros de la semana.
        irA(semana, avisar = false)
    }

    /** Cambia de semana conservando el día de la semana (sin pasar de hoy). */
    private fun irA(nueva: Semana, avisar: Boolean = true) {
        val hoy = LocalDate.now()
        val desfase = dia.toEpochDay() - semana.inicio.toEpochDay()
        semana = nueva
        dia = semana.inicio.plusDays(desfase).let { if (it.isAfter(hoy)) hoy else it }
        if (!semana.contiene(dia)) dia = semana.inicio
        litrosDe = emptyMap()
        f.tvSemana.text = "Semana ${semana.numero}: ${semana.textoConDias()}"
        // No hay leche del futuro: la semana actual es la última
        f.btnSemanaSiguiente.isEnabled = !semana.contiene(hoy) && semana.inicio.isBefore(hoy)
        f.btnSemanaSiguiente.alpha = if (f.btnSemanaSiguiente.isEnabled) 1f else 0.3f
        dibujarDias()
        if (avisar) alCambiar()

        val pedida = semana
        fragment.conApi({ RetrofitClient.api.getLecheDias(pedida.inicio.toString(), pedida.fin.toString()) }) { dias ->
            if (pedida != semana) return@conApi // el usuario ya pasó a otra semana
            litrosDe = dias.orEmpty().associateBy { it.fecha }
            dibujarDias()
            alCambiar()
        }
    }

    private fun dibujarDias() {
        val hoy = LocalDate.now()
        val contenedor = f.contenedorDias
        contenedor.removeAllViews()
        for (d in semana.dias()) {
            val futuro = d.isAfter(hoy)
            val elegido = d == dia
            val litros = litrosDe[d.toString()]?.litros ?: 0.0
            val boton = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(color(when {
                        elegido -> R.color.verde_principal
                        litros > 0 -> R.color.verde_muy_claro
                        else -> R.color.crema
                    }))
                    if (d == hoy && !elegido) setStroke(dp(2), color(R.color.verde_claro))
                }
                alpha = if (futuro) 0.35f else 1f
                isEnabled = !futuro
                contentDescription = "${Semana.diaLargo(d)}: ${Formato.litros(litros)}"
                if (!futuro) setOnClickListener { dia = d; dibujarDias(); alCambiar() }
            }
            val colorTexto = color(if (elegido) R.color.white else R.color.gris_texto_principal)
            boton.addView(texto(Semana.nombreDia(d), 11f, if (elegido) colorTexto else color(R.color.gris_texto_secundario)))
            boton.addView(texto(d.dayOfMonth.toString(), 16f, colorTexto, negrita = true))
            boton.addView(texto(
                if (litros > 0) "${Formato.numero(litros, 0)} L" else "—",
                10f,
                if (elegido) colorTexto else color(if (litros > 0) R.color.verde_oscuro else R.color.gris_texto_secundario)
            ))
            contenedor.addView(boton)
        }
    }

    private fun texto(valor: String, sp: Float, color: Int, negrita: Boolean = false) = TextView(ctx).apply {
        text = valor
        textSize = sp
        setTextColor(color)
        gravity = Gravity.CENTER
        maxLines = 1
        if (negrita) setTypeface(typeface, Typeface.BOLD)
    }

    private fun color(id: Int) = ContextCompat.getColor(ctx, id)
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
