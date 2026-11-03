package com.queseria.app.ui.liquidaciones

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.queseria.app.R
import com.queseria.app.models.FormaPago
import com.queseria.app.models.Liquidacion
import com.queseria.app.models.TipoDescuento
import com.queseria.app.util.Formato
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Genera el PDF de una liquidación (o, si está pagada, el comprobante de pago) en tamaño carta
 * y lo guarda en la caché de la app para verlo o compartirlo. Siempre se arma con los datos
 * actuales de la liquidación, así que "regenerar" es volver a llamar a [generar].
 */
object LiquidacionPdf {

    private const val ANCHO = 612   // carta: 8,5 × 11 pulgadas en puntos
    private const val ALTO = 792
    private const val MARGEN = 40f

    fun numeroTexto(numero: Int?) = if (numero == null) "Borrador" else "N° %04d".format(numero)

    fun archivo(context: Context, liq: Liquidacion): File {
        val carpeta = File(context.cacheDir, "liquidaciones").apply { mkdirs() }
        // "Pedro Pérez" → "Pedro_Perez" (sin tildes ni espacios, para que el nombre del archivo sea seguro)
        val nombre = java.text.Normalizer.normalize(liq.nombreGanadero, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        val numero = liq.numero?.let { "%04d".format(it) } ?: "borrador"
        val sufijo = if (liq.pagada) "_pagada" else ""
        return File(carpeta, "Liquidacion_${numero}_${nombre}${sufijo}.pdf")
    }

    fun generar(context: Context, liq: Liquidacion, nombreNegocio: String?): File {
        val doc = PdfDocument()
        // El PDF es papel blanco: siempre con los colores del modo claro, aunque la app esté en modo oscuro
        val claro = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        val dibujo = Dibujo(context.createConfigurationContext(claro), doc)
        dibujo.liquidacion(liq, nombreNegocio)
        dibujo.terminar()
        val archivo = archivo(context, liq)
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    private class Dibujo(private val ctx: Context, private val doc: PdfDocument) {
        private var numeroPagina = 0
        private lateinit var pagina: PdfDocument.Page
        private lateinit var c: Canvas
        private var y = 0f

        private fun color(id: Int) = ContextCompat.getColor(ctx, id)
        private fun fuente(id: Int): Typeface = ResourcesCompat.getFont(ctx, id) ?: Typeface.DEFAULT

        private val regular = fuente(R.font.poppins_regular)
        private val media = fuente(R.font.poppins_medium)
        private val fuerte = fuente(R.font.poppins_semibold)
        private val negrita = fuente(R.font.poppins_bold)

        private val verdeOscuro = color(R.color.verde_oscuro)
        private val verde = color(R.color.verde_principal)
        private val lima = color(R.color.verde_claro)
        private val crema = color(R.color.beige_fondo)
        private val cremaOscura = color(R.color.crema)
        private val oscuro = color(R.color.gris_texto_principal)
        private val gris = color(R.color.gris_texto_secundario)
        private val ambar = color(R.color.ambar_advertencia)

        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val relleno = Paint(Paint.ANTI_ALIAS_FLAG)

        init { nuevaPagina() }

        private fun nuevaPagina() {
            if (numeroPagina > 0) doc.finishPage(pagina)
            numeroPagina++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numeroPagina).create())
            c = pagina.canvas
            y = MARGEN
        }

        /** Si lo que sigue no cabe, pasa a otra página. */
        private fun asegurar(alto: Float) {
            if (y + alto > ALTO - 40) nuevaPagina()
        }

        fun terminar() {
            pie()
            doc.finishPage(pagina)
        }

        private fun texto(t: String, x: Float, yy: Float, tam: Float, tf: Typeface, col: Int, alin: Paint.Align = Paint.Align.LEFT) {
            p.textSize = tam
            p.typeface = tf
            p.color = col
            p.textAlign = alin
            c.drawText(t, x, yy, p)
        }

        private fun caja(l: Float, t: Float, r: Float, b: Float, col: Int, radio: Float = 10f) {
            relleno.color = col
            relleno.style = Paint.Style.FILL
            c.drawRoundRect(RectF(l, t, r, b), radio, radio, relleno)
        }

        fun liquidacion(liq: Liquidacion, negocio: String?) {
            val der = ANCHO - MARGEN
            val esComprobante = liq.pagada

            // ===== Encabezado: logo, marca y número =====
            BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_quesodata)?.let { logo ->
                c.drawBitmap(logo, null, RectF(MARGEN - 6, y - 6, MARGEN + 74, y + 74), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            texto("QuesoData", MARGEN + 86, y + 26, 24f, negrita, verdeOscuro)
            texto("Mi negocio, en orden", MARGEN + 86, y + 43, 11f, media, lima)
            negocio?.takeIf { it.isNotBlank() }?.let { texto(it, MARGEN + 86, y + 59, 11f, regular, gris) }

            texto(if (esComprobante) "COMPROBANTE DE PAGO" else "LIQUIDACIÓN SEMANAL", der, y + 20, 12f, fuerte, verde, Paint.Align.RIGHT)
            texto(numeroTexto(liq.numero), der, y + 46, 22f, negrita, verdeOscuro, Paint.Align.RIGHT)
            val fecha = if (esComprobante) liq.fechaPago else (liq.createdAt ?: Formato.hoyIso())
            texto("Fecha: ${Formato.fechaLegible(fecha)}", der, y + 64, 10.5f, regular, gris, Paint.Align.RIGHT)

            // Sello de estado
            val estado = if (esComprobante) "PAGADO" else if (liq.generada) "PENDIENTE" else "BORRADOR"
            val colorEstado = if (esComprobante) verde else ambar
            p.textSize = 10f; p.typeface = negrita
            val anchoSello = p.measureText(estado) + 24
            caja(der - anchoSello, y + 70, der, y + 88, (colorEstado and 0x00FFFFFF) or 0x22000000, 10f)
            texto(estado, der - anchoSello / 2, y + 83, 10f, negrita, colorEstado, Paint.Align.CENTER)

            y += 96
            relleno.color = lima
            c.drawRect(MARGEN, y, der, y + 3, relleno)
            y += 14

            // ===== Ganadero y semana =====
            val mitad = (MARGEN + der) / 2
            caja(MARGEN, y, mitad - 6, y + 64, crema)
            caja(mitad + 6, y, der, y + 64, crema)
            texto("GANADERO", MARGEN + 14, y + 17, 9f, fuerte, gris)
            texto(liq.nombreGanadero, MARGEN + 14, y + 37, 15f, fuerte, oscuro)
            texto("Tel. ${liq.telefonoGanadero ?: "—"}", MARGEN + 14, y + 54, 11f, regular, gris)

            val inicio = LocalDate.parse(Formato.soloFecha(liq.fechaInicio))
            val fin = LocalDate.parse(Formato.soloFecha(liq.fechaFin))
            texto("SEMANA LIQUIDADA · ${liq.semana}", mitad + 20, y + 17, 9f, fuerte, gris)
            texto("Viernes ${Formato.fechaLegible(inicio.toString())}", mitad + 20, y + 36, 12f, media, oscuro)
            texto("a jueves ${Formato.fechaLegible(fin.toString())}", mitad + 20, y + 53, 12f, media, oscuro)
            y += 80

            // ===== Tabla de litros por día =====
            val cols = floatArrayOf(MARGEN + 14, MARGEN + 150, der - 150, der - 14)
            caja(MARGEN, y, der, y + 26, verdeOscuro, 8f)
            texto("Día", cols[0], y + 17, 10.5f, fuerte, 0xFFFFFFFF.toInt())
            texto("Fecha", cols[1], y + 17, 10.5f, fuerte, 0xFFFFFFFF.toInt())
            texto("Entregas", cols[2], y + 17, 10.5f, fuerte, 0xFFFFFFFF.toInt(), Paint.Align.RIGHT)
            texto("Litros", cols[3], y + 17, 10.5f, fuerte, 0xFFFFFFFF.toInt(), Paint.Align.RIGHT)
            y += 26
            liq.dias.forEachIndexed { i, d ->
                val dia = LocalDate.parse(d.fecha)
                if (i % 2 == 1) { relleno.color = crema; c.drawRect(MARGEN, y, der, y + 19, relleno) }
                val nombreDia = dia.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "CO")).replaceFirstChar { it.uppercase() }
                texto(nombreDia, cols[0], y + 13.5f, 10.5f, media, oscuro)
                texto(Formato.fechaLegible(d.fecha), cols[1], y + 13.5f, 10.5f, regular, gris)
                texto(if (d.entregas == 0) "—" else d.entregas.toString(), cols[2], y + 13.5f, 10.5f, regular, gris, Paint.Align.RIGHT)
                texto(if (d.litros == 0.0) "—" else Formato.litros(d.litros), cols[3], y + 13.5f, 10.5f, if (d.litros > 0) fuerte else regular, oscuro, Paint.Align.RIGHT)
                y += 19
            }
            relleno.color = cremaOscura
            c.drawRect(MARGEN, y, der, y + 1.5f, relleno)
            texto("Total de la semana", cols[0], y + 19, 11f, fuerte, verdeOscuro)
            texto(liq.dias.sumOf { it.entregas }.toString(), cols[2], y + 19, 11f, fuerte, verdeOscuro, Paint.Align.RIGHT)
            texto(Formato.litros(liq.litros), cols[3], y + 19, 11f, negrita, verdeOscuro, Paint.Align.RIGHT)
            y += 42

            // ===== Liquidación =====
            val izq = mitad - 40
            fun fila(etiqueta: String, valor: String, col: Int = oscuro, tf: Typeface = media, tam: Float = 11.5f) {
                asegurar(22f)
                texto(etiqueta, izq, y, tam, regular, gris)
                texto(valor, der, y, tam, tf, col, Paint.Align.RIGHT)
                y += 19
            }
            fila("Total de litros", Formato.litros(liq.litros))
            fila("Precio por litro", Formato.moneda(liq.precioLitro))
            fila("Subtotal", Formato.moneda(liq.subtotal), tf = fuerte)

            if (liq.descuentos.isNotEmpty()) {
                y += 4
                asegurar(20f)
                texto("DESCUENTOS", izq, y, 9f, fuerte, gris)
                y += 18
                liq.descuentos.forEach { d ->
                    val detalle = buildString {
                        append(TipoDescuento.legible(d.tipo))
                        d.fecha?.let { append(" · ").append(Formato.fechaLegible(it).substringBeforeLast(" ")) }
                        if (d.tipo == TipoDescuento.LECHE_RECHAZADA && d.litros != null) append(" · ${Formato.litros(d.litros)}")
                    }
                    asegurar(34f)
                    texto(detalle, izq, y, 10.5f, media, oscuro)
                    texto("−${Formato.moneda(d.valor)}", der, y, 10.5f, media, ambar, Paint.Align.RIGHT)
                    y += 14
                    d.descripcion?.takeIf { it.isNotBlank() }?.let {
                        texto(it, izq, y, 9.5f, regular, gris)
                        y += 14
                    }
                    y += 2
                }
                fila("Total descuentos", "−${Formato.moneda(liq.totalDescuentos)}", ambar, fuerte)
            }

            // Total a pagar
            y += 6
            asegurar(56f)
            caja(izq - 14, y, der, y + 50, verdeOscuro, 12f)
            texto(if (esComprobante) "TOTAL PAGADO" else "TOTAL A PAGAR", izq, y + 31, 12f, fuerte, 0xFFFFFFFF.toInt())
            texto(Formato.moneda(liq.total), der - 14, y + 34, 20f, negrita, 0xFFFFFFFF.toInt(), Paint.Align.RIGHT)
            y += 62

            // ===== Pago =====
            asegurar(60f)
            if (esComprobante) {
                caja(MARGEN, y, der, y + 46, (verde and 0x00FFFFFF) or 0x1A000000)
                texto("Pagado el ${Formato.fechaLegible(liq.fechaPago)} · ${FormaPago.legible(liq.formaPago)}", MARGEN + 14, y + 20, 12f, fuerte, verdeOscuro)
                texto(liq.notas?.takeIf { it.isNotBlank() } ?: "Gracias por su leche.", MARGEN + 14, y + 36, 10f, regular, gris)
            } else {
                caja(MARGEN, y, der, y + 46, (ambar and 0x00FFFFFF) or 0x1A000000)
                texto("Estado: pendiente de pago", MARGEN + 14, y + 20, 12f, fuerte, ambar)
                texto("Semana de viernes a jueves. El jueves es el día de corte.", MARGEN + 14, y + 36, 10f, regular, gris)
            }
            y += 46
        }

        private fun pie() {
            val ahora = java.text.SimpleDateFormat("d MMM yyyy, h:mm a", Locale("es", "CO")).format(java.util.Date())
            texto("Generado con QuesoData · Mi negocio, en orden · $ahora", ANCHO / 2f, ALTO - 28f, 8.5f, regular, gris, Paint.Align.CENTER)
        }
    }
}
