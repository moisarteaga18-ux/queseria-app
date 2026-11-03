package com.queseria.app.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

/**
 * Aviso informativo verde de los formularios (estilo Queseria.Aviso, como "Leche recibida ese día: 70 L").
 * Se oculta solo mientras no tiene texto, para que no quede una caja verde vacía.
 */
class Aviso @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
