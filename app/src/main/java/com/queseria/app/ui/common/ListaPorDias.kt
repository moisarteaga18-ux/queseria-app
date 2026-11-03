package com.queseria.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.queseria.app.R
import com.queseria.app.databinding.ItemDiaSemanaBinding
import com.queseria.app.databinding.ItemFilaBinding
import com.queseria.app.databinding.VistaSemanaBinding
import com.queseria.app.util.Semana
import java.time.LocalDate

/**
 * Selector de semana de las listas (vista_semana.xml): flechas para ir a la semana anterior o siguiente.
 * No pasa de la semana actual. [alCambiar] recibe la semana elegida (no al crearlo).
 */
class NavegadorSemana(
    private val vista: VistaSemanaBinding,
    inicial: Semana = Semana.actual(),
    private val alCambiar: (Semana) -> Unit
) {
    var semana: Semana = inicial
        private set

    init {
        vista.btnSemanaAnterior.setOnClickListener { ir(semana.anterior()) }
        vista.btnSemanaSiguiente.setOnClickListener { ir(semana.siguiente()) }
        // Sin avisar aquí: quien lo crea todavía no tiene la referencia; carga después de crearlo
        ir(inicial, avisar = false)
    }

    fun ir(nueva: Semana, avisar: Boolean = true) {
        semana = nueva
        vista.tvSemana.text = "Semana ${semana.numero}" + if (semana.esActual) " · esta semana" else ""
        vista.tvSemanaDias.text = semana.textoConDias()
        val hayMas = semana.inicio.isBefore(Semana.actual().inicio)
        vista.btnSemanaSiguiente.isEnabled = hayMas
        vista.btnSemanaSiguiente.alpha = if (hayMas) 1f else 0.3f
        if (avisar) alCambiar(semana)
    }
}

/**
 * Lista de una semana agrupada por día, de viernes a jueves: un encabezado por día con su
 * [subtotal] y debajo sus registros (filas normales). Los días sin registros salen en gris.
 * [fechaDe] devuelve la fecha del registro (yyyy-MM-dd).
 */
class ListaPorDias<T>(
    private val fechaDe: (T) -> String,
    private val subtotal: (List<T>) -> String,
    private val aFila: (T) -> Fila,
    private val alTocar: ((T) -> Unit)? = null,
    private val alMantener: ((T) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private class Dia(val fecha: LocalDate, val subtotal: String?)
    private var items: List<Any?> = emptyList()

    /** Muestra la [semana] con sus [elementos] (los que no son de esa semana se ignoran). */
    fun mostrar(semana: Semana, elementos: List<T>) {
        val porDia = elementos.groupBy { fechaDe(it) }
        items = semana.dias().flatMap { d ->
            val delDia = porDia[d.toString()].orEmpty()
            listOf(Dia(d, delDia.takeIf { it.isNotEmpty() }?.let(subtotal))) + delDia
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = if (items[position] is Dia) TIPO_DIA else TIPO_FILA

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_DIA) DiaHolder(ItemDiaSemanaBinding.inflate(inflater, parent, false))
        else FilaHolder(ItemFilaBinding.inflate(inflater, parent, false))
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is DiaHolder -> holder.bind(item as Dia)
            is FilaHolder -> {
                val elemento = item as T
                holder.b.mostrar(aFila(elemento), alTocar?.let { { it(elemento) } }, alMantener?.let { { it(elemento) } })
            }
        }
    }

    private class FilaHolder(val b: ItemFilaBinding) : RecyclerView.ViewHolder(b.root)

    private class DiaHolder(val b: ItemDiaSemanaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(dia: Dia) {
            val ctx = b.root.context
            val vacio = dia.subtotal == null
            val nombre = Semana.diaLargo(dia.fecha)
            b.tvDia.text = if (dia.fecha == LocalDate.now()) "$nombre · hoy" else nombre
            b.tvSubtotal.text = dia.subtotal ?: "Sin registros"
            val color = ContextCompat.getColor(ctx, if (vacio) R.color.gris_texto_secundario else R.color.verde_oscuro)
            b.tvDia.setTextColor(color)
            b.tvSubtotal.setTextColor(color)
            b.root.alpha = if (vacio) 0.6f else 1f
            b.marca.visibility = if (vacio) View.INVISIBLE else View.VISIBLE
        }
    }

    private companion object {
        const val TIPO_DIA = 0
        const val TIPO_FILA = 1
    }
}

