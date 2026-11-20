package com.queseria.app.offline

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Un registro hecho sin internet, guardado en el teléfono hasta que el servidor lo reciba.
 * [cuerpo] es el JSON del pedido (LecheRequest, ProduccionRequest o VentaRequest) con su [idLocal]: al
 * reenviarlo el servidor no lo duplica. Solo se quita de aquí cuando el servidor confirma que lo guardó.
 * Si el servidor lo rechaza (por ejemplo, no hay stock) queda como [RECHAZADO] con su [error]: nunca se borra solo.
 */
data class Pendiente(
    val idLocal: String,
    val tipo: String,
    val cuerpo: String,
    /** Fecha del registro (yyyy-MM-dd), para mostrarlo en su día. */
    val fecha: String,
    /** "120 L de Juan", "venta de $ 48.000 a Rosa"… */
    val descripcion: String,
    /** Nombre del ganadero o del cliente (para mostrarlo en la lista). */
    val nombre: String? = null,
    /** Cuenta con la que se hizo (solo se sube con esa cuenta). */
    val usuario: String?,
    val creado: Long = System.currentTimeMillis(),
    val estado: String = PENDIENTE,
    val error: String? = null,
    /** El servidor pidió confirmar porque la semana ya está pagada. */
    val requiereConfirmacion: Boolean = false,
    /** El usuario ya aceptó cambiar la semana pagada (se reenvía con confirmar = true). */
    val confirmado: Boolean = false
) {
    val rechazado get() = estado == RECHAZADO

    companion object {
        const val PENDIENTE = "pendiente"
        const val RECHAZADO = "rechazado"
        const val LECHE = "leche"
        const val PRODUCCION = "produccion"
        const val VENTA = "venta"
    }
}

/** Cola de registros por subir, en un archivo del teléfono (sobrevive a cerrar la app y a cerrar sesión). */
object Pendientes {
    private val gson = Gson()
    private val tipoLista = object : TypeToken<List<Pendiente>>() {}.type
    private val _cambios = MutableLiveData<List<Pendiente>>(emptyList())
    /** La lista cada vez que cambia (para el aviso superior y las listas de Leche, Producción y Ventas). */
    val cambios: LiveData<List<Pendiente>> = _cambios

    private fun archivo(context: Context) = File(context.applicationContext.filesDir, "registros_por_subir.json")

    @Synchronized
    fun lista(context: Context): List<Pendiente> = try {
        val f = archivo(context)
        if (!f.exists()) emptyList() else gson.fromJson<List<Pendiente>>(f.readText(), tipoLista).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    /** Solo los de [usuario] (los de otra cuenta esperan a que esa cuenta vuelva a entrar). */
    fun de(context: Context, usuario: String?, tipo: String? = null) =
        lista(context).filter { it.usuario == usuario && (tipo == null || it.tipo == tipo) }

    @Synchronized
    private fun escribir(context: Context, lista: List<Pendiente>) {
        val f = archivo(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(gson.toJson(lista))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        _cambios.postValue(lista)
    }

    @Synchronized
    fun agregar(context: Context, p: Pendiente) = escribir(context, lista(context).filter { it.idLocal != p.idLocal } + p)

    @Synchronized
    fun actualizar(context: Context, p: Pendiente) =
        escribir(context, lista(context).map { if (it.idLocal == p.idLocal) p else it })

    /** Solo cuando el servidor confirmó que lo guardó. */
    @Synchronized
    fun subido(context: Context, idLocal: String) = escribir(context, lista(context).filter { it.idLocal != idLocal })

    /** Publica la lista actual (al abrir la app). */
    fun refrescar(context: Context) = _cambios.postValue(lista(context))
}
