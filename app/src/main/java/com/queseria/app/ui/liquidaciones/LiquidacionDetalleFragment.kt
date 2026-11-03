package com.queseria.app.ui.liquidaciones

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.DialogPagarBinding
import com.queseria.app.databinding.DialogVistaPdfBinding
import com.queseria.app.databinding.FragmentLiquidacionBinding
import com.queseria.app.databinding.ItemDescuentoBinding
import com.queseria.app.databinding.ItemDiaBinding
import com.queseria.app.models.FormaPago
import com.queseria.app.models.GenerarLiquidacionRequest
import com.queseria.app.models.LineaDescuento
import com.queseria.app.models.Liquidacion
import com.queseria.app.models.PagarLiquidacionRequest
import com.queseria.app.models.TipoDescuento
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.elegirFecha
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.toast
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Liquidación semanal de un ganadero: litros por día, precio, descuentos y total.
 * Permite generarla, registrar descuentos, marcarla como pagada y enviar el PDF por WhatsApp.
 * Se abre con [ARG_ID] (liquidación existente) o con ganadero + semana (puede no existir aún).
 */
class LiquidacionDetalleFragment : Fragment() {

    private var _binding: FragmentLiquidacionBinding? = null
    private val binding get() = _binding!!

    private var liquidacionId: String? = null
    private var liq: Liquidacion? = null
    private val negocio by lazy { SessionManager(requireContext()).obtenerNombreNegocio() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiquidacionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        liquidacionId = arguments?.getString(ARG_ID)
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.btnAgregarDescuento.setOnClickListener { agregarDescuento() }
        binding.filaPrecio.setOnClickListener { cambiarPrecio() }
        binding.btnWhatsapp.setOnClickListener { enviarPorWhatsapp() }
        binding.btnCompartir.setOnClickListener { compartir() }
        binding.btnVerPdf.setOnClickListener { verPdf() }
        cargar()
    }

    private fun cargar() {
        val id = liquidacionId
        if (id != null) {
            conApi({ RetrofitClient.api.getLiquidacion(id) }, binding.swipeRefresh) { it?.let(::mostrar) }
        } else {
            val args = requireArguments()
            conApi({
                RetrofitClient.api.getLiquidacionPrevia(
                    args.getString(ARG_GANADERO)!!, args.getInt(ARG_SEMANA), args.getInt(ARG_ANIO)
                )
            }, binding.swipeRefresh) { it?.let(::mostrar) }
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)

    private fun mostrar(l: Liquidacion) {
        liq = l
        l.id?.let { liquidacionId = it }
        val semana = Semana(LocalDate.parse(Formato.soloFecha(l.fechaInicio)))

        // Cabecera
        binding.tvNumero.text = if (l.generada) "Liquidación ${LiquidacionPdf.numeroTexto(l.numero)}" else "Liquidación · borrador"
        val (textoEstado, colorEstado) = when {
            l.pagada -> "Pagado" to R.color.verde_exito
            l.generada -> "Pendiente" to R.color.ambar_advertencia
            else -> "Sin generar" to R.color.gris_texto_secundario
        }
        binding.tvEstado.text = textoEstado
        binding.tvEstado.setTextColor(color(colorEstado))
        binding.tvEstado.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        binding.tvGanadero.text = l.nombreGanadero
        binding.tvTelefono.text = l.telefonoGanadero ?: "Sin teléfono registrado"
        binding.tvSemana.text = "Semana ${l.semana} · vie ${Formato.fechaLegible(semana.inicio.toString()).substringBeforeLast(" ")} a jue ${Formato.fechaLegible(semana.fin.toString())}"
        val diferencia = l.diferenciaPago
        val corregida = kotlin.math.abs(diferencia) >= 1
        binding.tvRotuloTotal.text = when {
            l.pagada && corregida -> "Total corregido"
            l.pagada -> "Total pagado"
            else -> "Total a pagar"
        }
        // Sin precio del litro el total no se puede calcular (solo quedarían los descuentos en negativo)
        binding.tvTotalCabecera.text = if (l.tienePrecio) Formato.moneda(l.total) else "Falta el precio"

        // Aviso
        val aviso = when {
            // Se corrigieron datos de la semana después de pagarla (con confirmación)
            l.pagada && corregida ->
                "Después del pago se corrigieron datos de esta semana. Se pagaron ${Formato.moneda(l.totalPagado ?: 0.0)} " +
                    "y el total ahora es ${Formato.moneda(l.total)}: " +
                    if (diferencia > 0) "falta pagarle ${Formato.moneda(diferencia)}." else "se le pagaron ${Formato.moneda(-diferencia)} de más."
            !l.tienePrecio -> "Falta el precio del litro de esta semana. Toca «Definir precio del litro» para calcular la liquidación."
            !l.generada && l.litros <= 0 -> "Este ganadero no tiene leche registrada en esta semana."
            !l.generada && !semana.fin.isBefore(LocalDate.now()) ->
                "La semana cierra el jueves ${Formato.fechaLegible(semana.fin.toString())}. Puedes generar la liquidación ahora o al corte."
            else -> null
        }
        binding.tvAviso.text = aviso
        binding.tvAviso.visibility = if (aviso == null) View.GONE else View.VISIBLE

        mostrarDias(l)
        binding.tvTotalLitros.text = Formato.litros(l.litros)
        binding.tvPrecio.text = if (l.tienePrecio) "${Formato.moneda(l.precioLitro)} / L" else "Sin definir"
        binding.ivEditarPrecio.visibility = if (l.pagada) View.GONE else View.VISIBLE
        binding.filaPrecio.isClickable = !l.pagada
        binding.tvSubtotal.text = Formato.moneda(l.subtotal)
        mostrarDescuentos(l)
        binding.btnAgregarDescuento.visibility = if (l.pagada) View.GONE else View.VISIBLE
        binding.tvTotal.text = if (l.tienePrecio) Formato.moneda(l.total) else "—"

        // Pago
        binding.cardPago.visibility = if (l.pagada) View.VISIBLE else View.GONE
        binding.tvPago.text = "Pagado el ${Formato.fechaLegible(l.fechaPago)} · ${FormaPago.legible(l.formaPago)}"
        binding.tvPagoNotas.text = l.notas ?: "El PDF ahora es el comprobante de pago."

        // Acciones: generar → enviar (WhatsApp, compartir, ver PDF) → marcar como pagado
        binding.tvPasoEnviar.visibility = if (l.generada) View.VISIBLE else View.GONE
        binding.tvPasoEnviar.text = if (l.pagada) "Enviar el comprobante" else "Enviar al ganadero"
        binding.filaEnviar.visibility = if (l.generada) View.VISIBLE else View.GONE
        binding.btnVerPdf.visibility = if (l.generada) View.VISIBLE else View.GONE
        when {
            !l.generada && !l.tienePrecio -> {
                binding.btnPrincipal.visibility = View.VISIBLE
                binding.btnPrincipal.text = "Definir precio del litro"
                binding.btnPrincipal.isEnabled = true
                binding.btnPrincipal.setOnClickListener { cambiarPrecio() }
                binding.btnSecundario.visibility = View.GONE
            }
            !l.generada -> {
                binding.btnPrincipal.visibility = View.VISIBLE
                binding.btnPrincipal.text = "Generar liquidación"
                binding.btnPrincipal.isEnabled = l.litros > 0
                binding.btnPrincipal.setOnClickListener { generar() }
                binding.btnSecundario.visibility = View.GONE
            }
            !l.pagada -> {
                binding.btnPrincipal.visibility = View.VISIBLE
                binding.btnPrincipal.text = "Marcar como pagado"
                binding.btnPrincipal.isEnabled = true
                binding.btnPrincipal.setOnClickListener { marcarPagado() }
                binding.btnSecundario.visibility = View.VISIBLE
                binding.btnSecundario.text = "Anular liquidación"
                binding.btnSecundario.setTextColor(color(R.color.rojo_error))
                binding.btnSecundario.setOnClickListener { anular() }
            }
            else -> {
                binding.btnPrincipal.visibility = View.GONE
                binding.btnSecundario.visibility = View.VISIBLE
                binding.btnSecundario.text = "Deshacer pago"
                binding.btnSecundario.setTextColor(color(R.color.gris_texto_secundario))
                binding.btnSecundario.setOnClickListener { deshacerPago() }
            }
        }
    }

    private fun mostrarDias(l: Liquidacion) {
        val contenedor = binding.contenedorDias
        contenedor.removeAllViews()
        l.dias.forEachIndexed { i, d ->
            val fila = ItemDiaBinding.inflate(layoutInflater, contenedor, false)
            val fecha = LocalDate.parse(d.fecha)
            val nombre = fecha.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "CO")).replaceFirstChar { it.uppercase() }
            fila.tvDia.text = "$nombre ${Formato.fechaLegible(d.fecha).substringBeforeLast(" ")}"
            fila.tvEntregas.text = when (d.entregas) {
                0 -> ""
                1 -> "1 entrega"
                else -> "${d.entregas} entregas"
            }
            fila.tvLitros.text = if (d.litros > 0) Formato.litros(d.litros) else "—"
            fila.tvLitros.setTextColor(color(if (d.litros > 0) R.color.gris_texto_principal else R.color.gris_texto_secundario))
            if (i % 2 == 0) fila.root.setBackgroundResource(R.drawable.bg_chip)
            if (i % 2 == 0) fila.root.backgroundTintList = ColorStateList.valueOf(color(R.color.beige_fondo))
            contenedor.addView(fila.root)
        }
    }

    private fun mostrarDescuentos(l: Liquidacion) {
        val contenedor = binding.contenedorDescuentos
        contenedor.removeAllViews()
        binding.tvSinDescuentos.visibility = if (l.descuentos.isEmpty()) View.VISIBLE else View.GONE
        l.descuentos.forEach { d ->
            val fila = ItemDescuentoBinding.inflate(layoutInflater, contenedor, false)
            fila.ivTipo.setImageResource(iconoDescuento(d.tipo))
            fila.tvTipo.text = TipoDescuento.legible(d.tipo)
            fila.tvDetalle.text = listOfNotNull(
                d.fecha?.let { Formato.fechaLegible(it) },
                d.litros?.takeIf { d.tipo == TipoDescuento.LECHE_RECHAZADA }?.let { "${Formato.litros(it)} × ${Formato.moneda(l.precioLitro)}" },
                d.descripcion
            ).joinToString(" · ")
            fila.tvValor.text = "−${Formato.moneda(d.valor)}"
            fila.btnEliminar.visibility = if (l.pagada || d.descuentoId == null) View.GONE else View.VISIBLE
            fila.btnEliminar.setOnClickListener { eliminarDescuento(d) }
            contenedor.addView(fila.root)
        }
    }

    private fun iconoDescuento(tipo: String) = when (tipo) {
        TipoDescuento.ADELANTO -> R.drawable.ic_pagos
        TipoDescuento.PRESTAMO -> R.drawable.ic_precios
        TipoDescuento.LECHE_RECHAZADA -> R.drawable.ic_leche
        else -> R.drawable.ic_ajuste
    }

    // ========================================
    // ACCIONES
    // ========================================

    private fun generar() {
        val l = liq ?: return
        binding.btnPrincipal.isEnabled = false // evita generar dos veces con un doble toque
        conApi({ RetrofitClient.api.generarLiquidacion(GenerarLiquidacionRequest(l.ganaderoId, l.semana, l.anio)) },
            alFallar = { binding.btnPrincipal.isEnabled = true }) { nueva ->
            nueva?.let {
                mostrar(it)
                ofrecerEnvio(it)
            }
        }
    }

    /** Recién generada: se ofrece enviarla de una vez (siguiente paso del flujo). */
    private fun ofrecerEnvio(l: Liquidacion) {
        AlertDialog.Builder(requireContext())
            .setTitle("Liquidación ${LiquidacionPdf.numeroTexto(l.numero)} lista")
            .setMessage("Total a pagar a ${l.nombreGanadero}: ${Formato.moneda(l.total)}.\n¿Se la envías ahora en PDF?")
            .setPositiveButton("WhatsApp") { _, _ -> enviarPorWhatsapp() }
            .setNeutralButton("Compartir") { _, _ -> compartir() }
            .setNegativeButton("Después", null)
            .show()
    }

    private fun agregarDescuento() {
        val l = liq ?: return
        val semana = Semana(LocalDate.parse(Formato.soloFecha(l.fechaInicio)))
        abrirFormularioDescuento(l.ganaderoId, l.nombreGanadero, semana) { cargar() }
    }

    private fun eliminarDescuento(d: LineaDescuento) {
        val id = d.descuentoId ?: return
        confirmar("Eliminar descuento", "¿Quitar ${TipoDescuento.legible(d.tipo).lowercase()} de ${Formato.moneda(d.valor)}?") {
            conApi({ RetrofitClient.api.eliminarDescuento(id) }) {
                toast("Descuento eliminado")
                cargar()
            }
        }
    }

    private fun cambiarPrecio() {
        val l = liq ?: return
        if (l.pagada) return
        val semana = Semana(LocalDate.parse(Formato.soloFecha(l.fechaInicio)))
        abrirPrecioLeche(semana, l.precioLitro.takeIf { l.tienePrecio }) { cargar() }
    }

    private fun marcarPagado() {
        val l = liq ?: return
        val id = l.id ?: return
        val f = DialogPagarBinding.inflate(layoutInflater)
        var fecha = Formato.hoyIso()
        f.tvResumen.text = "${l.nombreGanadero} · ${LiquidacionPdf.numeroTexto(l.numero)}\nTotal: ${Formato.moneda(l.total)}"
        f.tvFecha.text = Formato.fechaLegible(fecha)
        f.tvFecha.setOnClickListener { elegirFecha(fecha) { fecha = it; f.tvFecha.text = Formato.fechaLegible(it) } }

        mostrarFormulario("Marcar como pagado", f.root, textoGuardar = "Pagado") { dialogo ->
            val forma = when {
                f.rbNequi.isChecked -> FormaPago.NEQUI
                f.rbTransferencia.isChecked -> FormaPago.TRANSFERENCIA
                else -> FormaPago.EFECTIVO
            }
            val request = PagarLiquidacionRequest(forma, fecha, f.etNotas.text.toString().trim().ifEmpty { null })
            guardarEnApi(dialogo, "Listo: pago registrado", { RetrofitClient.api.pagarLiquidacion(id, request) }) { pagada ->
                pagada?.let {
                    mostrar(it)
                    ofrecerComprobante(it)
                }
            }
            false
        }
    }

    /** Después de pagar: el PDF ya sale como comprobante; se ofrece enviarlo de una vez. */
    private fun ofrecerComprobante(l: Liquidacion) {
        AlertDialog.Builder(requireContext())
            .setTitle("Comprobante listo")
            .setMessage("¿Le envías el comprobante de pago a ${l.nombreGanadero} por WhatsApp?")
            .setPositiveButton("Enviar") { _, _ -> enviarPorWhatsapp() }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun deshacerPago() {
        val id = liq?.id ?: return
        confirmar("Deshacer pago", "La liquidación vuelve a quedar pendiente y se recalcula con los datos actuales.", textoSi = "Deshacer") {
            conApi({ RetrofitClient.api.deshacerPagoLiquidacion(id) }) {
                toast("La liquidación quedó pendiente")
                it?.let(::mostrar)
            }
        }
    }

    private fun anular() {
        val l = liq ?: return
        val id = l.id ?: return
        confirmar(
            "Anular liquidación",
            "¿Anular la liquidación ${LiquidacionPdf.numeroTexto(l.numero)}? Su número no se vuelve a usar. La leche y los descuentos no se borran.\n\n" +
                "No se volverá a generar sola: si la necesitas, ábrela de nuevo y toca «Generar liquidación».",
            textoSi = "Anular"
        ) {
            conApi({ RetrofitClient.api.anularLiquidacion(id) }) {
                toast("Liquidación anulada")
                findNavController().navigateUp()
            }
        }
    }

    // ========================================
    // PDF
    // ========================================

    /** Genera el PDF con los datos actuales y luego ejecuta [accion] con el archivo. */
    private fun conPdf(accion: (Liquidacion, File) -> Unit) {
        val l = liq ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val archivo = try {
                withContext(Dispatchers.IO) { LiquidacionPdf.generar(requireContext(), l, negocio) }
            } catch (e: Exception) {
                toast("No se pudo crear el PDF: ${e.message}")
                return@launch
            }
            if (view != null) accion(l, archivo)
        }
    }

    private fun mensaje(l: Liquidacion): String {
        val semana = Semana(LocalDate.parse(Formato.soloFecha(l.fechaInicio)))
        val firma = negocio?.let { "\n— $it" } ?: ""
        return if (l.pagada) {
            "Hola ${l.nombreGanadero}, te envío el comprobante de pago ${LiquidacionPdf.numeroTexto(l.numero)} " +
                "de la leche de la semana ${semana.texto()}: ${Formato.moneda(l.total)}, " +
                "pagado el ${Formato.fechaLegible(l.fechaPago)} por ${FormaPago.enFrase(l.formaPago)}.$firma"
        } else {
            "Hola ${l.nombreGanadero}, te envío la liquidación ${LiquidacionPdf.numeroTexto(l.numero)} " +
                "de la semana ${semana.texto()}: ${Formato.litros(l.litros)}, total a pagar ${Formato.moneda(l.total)}.$firma"
        }
    }

    private fun enviarPorWhatsapp() = conPdf { l, archivo ->
        if (l.telefonoGanadero.isNullOrBlank()) toast("${l.nombreGanadero} no tiene teléfono: elige el chat en WhatsApp")
        if (!CompartirPdf.porWhatsapp(requireContext(), archivo, l.telefonoGanadero, mensaje(l))) {
            toast("WhatsApp no está instalado; elige otra app")
            CompartirPdf.porOtrasApps(requireContext(), archivo, mensaje(l), archivo.nameWithoutExtension)
        }
    }

    private fun compartir() = conPdf { l, archivo ->
        CompartirPdf.porOtrasApps(requireContext(), archivo, mensaje(l), archivo.nameWithoutExtension)
    }

    /** Muestra el PDF dentro de la app (así se puede revisar aunque el teléfono no tenga visor de PDF). */
    private fun verPdf() = conPdf { l, archivo ->
        val vista = DialogVistaPdfBinding.inflate(layoutInflater)
        ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val ancho = resources.displayMetrics.widthPixels
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { pagina ->
                        val bmp = Bitmap.createBitmap(ancho, ancho * pagina.height / pagina.width, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        pagina.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        vista.contenedorPaginas.addView(ImageView(requireContext()).apply {
                            setImageBitmap(bmp)
                            adjustViewBounds = true
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            elevation = 4f
                        })
                    }
                }
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle(LiquidacionPdf.numeroTexto(l.numero) + " · " + l.nombreGanadero)
            .setView(vista.root)
            .setPositiveButton("WhatsApp") { _, _ -> enviarPorWhatsapp() }
            .setNeutralButton("Compartir") { _, _ -> compartir() }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "liquidacionId"
        const val ARG_GANADERO = "ganaderoId"
        const val ARG_SEMANA = "semana"
        const val ARG_ANIO = "anio"

        fun argsExistente(id: String) = bundleOf(ARG_ID to id)
        fun argsSemana(ganaderoId: String, semana: Int, anio: Int) =
            bundleOf(ARG_GANADERO to ganaderoId, ARG_SEMANA to semana, ARG_ANIO to anio)
    }
}
