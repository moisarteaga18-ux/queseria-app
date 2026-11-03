package com.queseria.app.ui.common

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.queseria.app.R
import com.queseria.app.databinding.ItemFilaBinding

/**
 * Lo que se muestra en una fila de cualquier lista de la app.
 * [icono] es un drawable opcional que va en un círculo del color [colorIcono]; [imagen] es una foto que lo
 * reemplaza (queso en Ventas) y [valorEnPildora] pone el valor en una píldora verde.
 */
data class Fila(
    val titulo: String,
    val subtitulo: String? = null,
    val detalle: String? = null,
    val valor: String? = null,
    val colorValor: Int = R.color.verde_oscuro,
    val etiqueta: String? = null,
    val colorEtiqueta: Int = R.color.ambar_advertencia,
    val icono: Int? = null,
    val colorIcono: Int = R.color.verde_principal,
    val imagen: Int? = null,
    val valorEnPildora: Boolean = false
)

/**
 * Adapter genérico: recibe una lista de elementos y una función que convierte cada uno en [Fila].
 * Así todas las listas (leche, ventas, productos, clientes...) comparten el mismo diseño.
 */
class FilaAdapter<T>(
    private val aFila: (T) -> Fila,
    private val alTocar: ((T) -> Unit)? = null,
    private val alMantener: ((T) -> Unit)? = null
) : RecyclerView.Adapter<FilaAdapter<T>.FilaViewHolder>() {

    private var elementos: List<T> = emptyList()

    fun actualizar(nuevos: List<T>) {
        elementos = nuevos
        notifyDataSetChanged()
    }

    override fun getItemCount() = elementos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilaViewHolder {
        val binding = ItemFilaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilaViewHolder, position: Int) {
        holder.bind(elementos[position])
    }

    inner class FilaViewHolder(private val b: ItemFilaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(elemento: T) = b.mostrar(aFila(elemento), alTocar?.let { { it(elemento) } }, alMantener?.let { { it(elemento) } })
    }
}

/** Llena una fila (item_fila) con [fila]. La usan [FilaAdapter] y las listas agrupadas por día. */
fun ItemFilaBinding.mostrar(fila: Fila, alTocar: (() -> Unit)?, alMantener: (() -> Unit)?) {
    val b = this
    val ctx = b.root.context

    b.tvTitulo.text = fila.titulo
    b.tvSubtitulo.mostrarTexto(fila.subtitulo)
    b.tvDetalle.mostrarTexto(fila.detalle)
    b.tvValor.mostrarTexto(fila.valor)
    if (fila.valorEnPildora) {
        // Valor en píldora verde con letra blanca (guía de diseño)
        b.tvValor.setBackgroundResource(R.drawable.bg_etiqueta)
        b.tvValor.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.verde_principal))
        b.tvValor.setTextColor(ContextCompat.getColor(ctx, R.color.white))
    } else {
        b.tvValor.background = null
        b.tvValor.setTextColor(ContextCompat.getColor(ctx, fila.colorValor))
    }

    b.tvEtiqueta.mostrarTexto(fila.etiqueta)
    val colorEtiqueta = ContextCompat.getColor(ctx, fila.colorEtiqueta)
    b.tvEtiqueta.setTextColor(colorEtiqueta)
    b.tvEtiqueta.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(colorEtiqueta, 0x22))

    b.ivImagen.visibility = if (fila.imagen != null) View.VISIBLE else View.GONE
    fila.imagen?.let { b.ivImagen.setImageResource(it); b.ivImagen.clipToOutline = true }
    if (fila.icono == null || fila.imagen != null) {
        b.contenedorIcono.visibility = View.GONE
    } else {
        val colorIcono = ContextCompat.getColor(ctx, fila.colorIcono)
        b.contenedorIcono.visibility = View.VISIBLE
        b.ivIcono.setImageResource(fila.icono)
        b.ivIcono.imageTintList = ColorStateList.valueOf(colorIcono)
        b.contenedorIcono.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(colorIcono, 0x1F))
    }

    b.root.setOnClickListener(alTocar?.let { { _: View -> it() } })
    b.root.setOnLongClickListener(alMantener?.let { { _: View -> it(); true } })
    b.root.isClickable = alTocar != null || alMantener != null
}

private fun android.widget.TextView.mostrarTexto(texto: String?) {
    text = texto
    visibility = if (texto.isNullOrBlank()) View.GONE else View.VISIBLE
}
