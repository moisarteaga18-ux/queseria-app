package com.queseria.app.ui.liquidaciones

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Envía el PDF de una liquidación: directo al chat de WhatsApp del ganadero o por cualquier app.
 */
object CompartirPdf {

    private val PAQUETES_WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun uri(context: Context, archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)

    /**
     * Número en formato internacional sin "+" (lo que usa WhatsApp). Los celulares colombianos
     * de 10 dígitos que empiezan por 3 se completan con el indicativo 57.
     */
    fun numeroWhatsapp(telefono: String?): String? {
        val digitos = telefono?.filter { it.isDigit() } ?: return null
        return when {
            digitos.length == 10 && digitos.startsWith("3") -> "57$digitos"
            digitos.length >= 11 -> digitos
            else -> null
        }
    }

    /**
     * Abre WhatsApp (o WhatsApp Business) con el PDF adjunto y el chat del ganadero elegido.
     * Devuelve false si WhatsApp no está instalado.
     */
    fun porWhatsapp(context: Context, archivo: File, telefono: String?, mensaje: String): Boolean {
        val numero = numeroWhatsapp(telefono)
        for (paquete in PAQUETES_WHATSAPP) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                setPackage(paquete)
                putExtra(Intent.EXTRA_STREAM, uri(context, archivo))
                putExtra(Intent.EXTRA_TEXT, mensaje)
                // "jid" hace que WhatsApp abra directamente el chat de ese número
                numero?.let { putExtra("jid", "$it@s.whatsapp.net") }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // Probar con el siguiente paquete
            }
        }
        return false
    }

    /** Hoja de compartir de Android (correo, Telegram, Drive...). */
    fun porOtrasApps(context: Context, archivo: File, mensaje: String, titulo: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri(context, archivo))
            putExtra(Intent.EXTRA_TEXT, mensaje)
            putExtra(Intent.EXTRA_SUBJECT, titulo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar liquidación"))
    }
}
