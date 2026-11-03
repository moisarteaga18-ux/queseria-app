package com.queseria.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.databinding.CabeceraProduccionBinding
import com.queseria.app.databinding.CabeceraTabsBinding
import com.queseria.app.databinding.DialogLecheBinding
import com.queseria.app.databinding.DialogProduccionBinding
import com.queseria.app.databinding.FragmentListaBinding
import com.queseria.app.models.Ganadero
import com.queseria.app.models.Leche
import com.queseria.app.models.LecheRequest
import com.queseria.app.models.Produccion
import com.queseria.app.models.ProduccionRequest
import com.queseria.app.models.Producto
import com.queseria.app.ui.common.Fila
import com.queseria.app.ui.common.FilaAdapter
import com.queseria.app.ui.common.ListaPorDias
import com.queseria.app.ui.common.NavegadorSemana
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.elegirFecha
import com.queseria.app.ui.common.guardarConConfirmacion
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.common.mostrarFormulario
import com.queseria.app.ui.common.preguntar
import com.queseria.app.ui.common.toast
import com.queseria.app.ui.ganaderos.abrirFormularioGanadero
import com.queseria.app.util.Formato
import com.queseria.app.util.Semana
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Dos pantallas con el mismo código (según el argumento [ARG_MODO]):
 * - "Leche recibida": litros recibidos por día y ganadero. La fecha se elige con un calendario
 *   por semanas (viernes a jueves) que muestra los litros ya registrados cada día.
 * - "Producción del día": leche convertida en queso (con su rendimiento), por semana (viernes a jueves)
 *   con el resumen de la semana y la lista agrupada por día. Los litros usados se
 *   llenan con la leche recibida ese día; al registrarla se puede sumar el queso al inventario.
 * Tocar un registro = corregirlo (el backend recalcula liquidaciones, inventario, etc.).
 * Mantener presionado un registro = eliminarlo.
 */
class LecheFragment : Fragment() {

    private var _binding: FragmentListaBinding? = null
    private val binding get() = _binding!!
    // Leche: resumen de hoy. Producción: selector de semana y resumen de la semana.
    private var cabeceraLeche: CabeceraTabsBinding? = null
    private var cabeceraProduccion: CabeceraProduccionBinding? = null
    private var navegador: NavegadorSemana? = null

    private var leche: List<Leche> = emptyList()
    /** Producción reciente (para proponer el producto en el formulario). */
    private var produccion: List<Produccion> = emptyList()
    /** Producción de la semana elegida. */
    private var produccionSemana: List<Produccion> = emptyList()

    private val enLeche by lazy { arguments?.getString(ARG_MODO) != MODO_PRODUCCION }

    private val adapterLeche = FilaAdapter<Leche>(
        aFila = { l ->
            val sinGanadero = l.ganaderoId == null
            Fila(
                titulo = if (sinGanadero) "Sin ganadero" else l.nombreGanadero ?: "Ganadero",
                subtitulo = Formato.fechaLegible(l.fecha),
                detalle = if (sinGanadero) "Toca para elegir el ganadero (si no, no entra en su liquidación)" else l.observaciones,
                valor = Formato.litros(l.litros),
                etiqueta = if (sinGanadero) "Asignar" else "${Formato.numero(l.litros / LITROS_POR_LATA, 1)} latas",
                colorEtiqueta = if (sinGanadero) R.color.ambar_advertencia else R.color.gris_texto_secundario,
                icono = R.drawable.ic_ganadero,
                colorIcono = if (sinGanadero) R.color.ambar_advertencia else R.color.verde_principal
            )
        },
        alTocar = { abrirFormularioLeche(it) },
        alMantener = { eliminarLeche(it) }
    )

    // Producción de la semana agrupada por día (viernes a jueves), con el subtotal de cada día
    private val adapterProduccion = ListaPorDias<Produccion>(
        fechaDe = { Formato.soloFecha(it.fecha) },
        subtotal = { del -> "${Formato.kg(del.sumOf { it.kgProducidos })} · ${Formato.litros(del.sumOf { it.litrosUsados })}" },
        aFila = { p ->
            Fila(
                titulo = Formato.fechaLegible(p.fecha),
                subtitulo = "${Formato.litros(p.litrosUsados)} → ${Formato.kg(p.kgProducidos)}",
                detalle = p.observaciones,
                valor = Formato.kg(p.kgProducidos),
                etiqueta = "${Formato.numero(librasPorLata(p.litrosUsados, p.kgProducidos))} lb/lata",
                colorEtiqueta = R.color.azul_info,
                icono = R.drawable.ic_produccion,
                colorIcono = R.color.azul_info
            )
        },
        alTocar = { abrirFormularioProduccion(it) },
        alMantener = { eliminarProduccion(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        if (enLeche) cabeceraLeche = CabeceraTabsBinding.inflate(inflater, binding.contenedorCabecera, true)
        else cabeceraProduccion = CabeceraProduccionBinding.inflate(inflater, binding.contenedorCabecera, true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLista.adapter = if (enLeche) adapterLeche else adapterProduccion
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.fabAgregar.text = if (enLeche) "Registrar leche" else "Registrar producción"
        binding.fabAgregar.setOnClickListener {
            if (enLeche) abrirFormularioLeche(null) else abrirFormularioProduccion(null)
        }
        cabeceraLeche?.tabs?.visibility = View.GONE
        if (enLeche) {
            mostrar()
            cargar()
        } else {
            // Semana de viernes a jueves (por defecto la actual); cambiarla vuelve a cargar
            navegador = NavegadorSemana(cabeceraProduccion!!.semana) { mostrar(); cargar() }
            mostrar()
            cargar()
        }

        // Desde el menú de Inicio se abre el formulario directamente
        if (arguments?.getBoolean(InicioFragment.ARG_NUEVO) == true) {
            arguments?.remove(InicioFragment.ARG_NUEVO)
            if (enLeche) abrirFormularioLeche(null) else abrirFormularioProduccion(null)
        }
    }

    private fun cargar() {
        if (enLeche) {
            conApi({ RetrofitClient.api.getLeche(limit = 60) }, binding.swipeRefresh) {
                leche = it.orEmpty()
                adapterLeche.actualizar(leche)
                mostrar()
            }
        } else {
            // La ruta no filtra por fechas: se trae la producción reciente y se toma la de la semana elegida
            val semana = navegador?.semana ?: return
            conApi({ RetrofitClient.api.getProduccion(limit = 1000) }, binding.swipeRefresh) { lista ->
                if (semana != navegador?.semana) return@conApi
                produccion = lista.orEmpty()
                produccionSemana = produccion.filter { semana.contiene(LocalDate.parse(Formato.soloFecha(it.fecha))) }
                mostrar()
            }
        }
    }

    private fun mostrar() {
        val cabecera = cabeceraLeche
        if (cabecera != null) {
            val litrosHoy = leche.filter { Formato.soloFecha(it.fecha) == Formato.hoyIso() }.sumOf { it.litros }
            val sinGanadero = leche.count { it.ganaderoId == null }
            cabecera.tvResumen.text = "Hoy: ${Formato.litros(litrosHoy)} · ${Formato.numero(litrosHoy / LITROS_POR_LATA, 1)} latas" +
                if (sinGanadero > 0) "\n$sinGanadero ${if (sinGanadero == 1) "registro" else "registros"} sin ganadero: tócalos para asignarlo" else ""
            binding.tvVacio.text = "Aún no has registrado leche.\nToca + Registrar leche para anotar la de hoy."
            binding.tvVacio.visibility = if (leche.isEmpty()) View.VISIBLE else View.GONE
            cabecera.tvResumen.visibility = View.VISIBLE
        } else {
            // Resumen de la semana elegida y la lista por días (los vacíos dicen "Sin registros")
            val c = cabeceraProduccion ?: return
            val semana = navegador?.semana ?: return
            val litros = produccionSemana.sumOf { it.litrosUsados }
            val kg = produccionSemana.sumOf { it.kgProducidos }
            c.tvLitros.text = Formato.litros(litros)
            c.tvKg.text = Formato.kg(kg)
            c.tvKgDetalle.text = "queso · ${Formato.numero(Formato.kgALibras(kg))} lb"
            c.tvRendimiento.text = if (litros > 0) Formato.numero(librasPorLata(litros, kg)) else "—"
            adapterProduccion.mostrar(semana, produccionSemana)
            binding.tvVacio.visibility = View.GONE
        }
    }

    // ========================================
    // LECHE
    // ========================================

    /** [existente] = corregir un registro; null = registrar uno nuevo. */
    private fun abrirFormularioLeche(existente: Leche?) {
        conApi({ RetrofitClient.api.getGanaderos() }) { mostrarFormularioLeche(it.orEmpty(), existente) }
    }

    private fun mostrarFormularioLeche(lista: List<Ganadero>, existente: Leche?) {
        val f = DialogLecheBinding.inflate(layoutInflater)
        var ganaderos = lista

        f.etLitros.doAfterTextChanged {
            val litros = Formato.leerNumero(it) ?: 0.0
            f.tvLatas.text = "= ${Formato.numero(litros / LITROS_POR_LATA, 1)} latas de 20 L"
        }
        existente?.let {
            f.etLitros.setText(Formato.campo(it.litros))
            f.etObservaciones.setText(it.observaciones.orEmpty())
        }

        fun llenarGanaderos(seleccionarId: String? = null) {
            val nombres = if (ganaderos.isEmpty()) listOf("— Primero agrega un ganadero —")
            else (if (existente != null && existente.ganaderoId == null) listOf("— Elige el ganadero —") else emptyList()) + ganaderos.map { it.nombre }
            f.spGanadero.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nombres)
            // Por defecto, el del registro o el del último registro de leche
            val id = seleccionarId ?: existente?.ganaderoId ?: leche.firstOrNull()?.ganaderoId.takeIf { existente == null }
            val desfase = nombres.size - ganaderos.size
            ganaderos.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { f.spGanadero.setSelection(it + desfase) }
        }
        fun ganaderoElegido(): Ganadero? = ganaderos.getOrNull(f.spGanadero.selectedItemPosition - (f.spGanadero.count - ganaderos.size))
        llenarGanaderos()

        // Calendario por semanas (viernes a jueves): por defecto la semana y el día de hoy
        lateinit var selector: SelectorSemana
        fun mostrarDia() {
            val dia = selector.datosDelDia
            val ganadero = ganaderoElegido()
            val delGanadero = dia?.porGanadero?.firstOrNull { it.ganaderoId == ganadero?.id }?.litros ?: 0.0
            f.tvDia.text = Semana.diaLargo(selector.dia) + when {
                dia == null -> ""
                dia.litros <= 0 -> " · todavía no hay leche registrada"
                else -> " · ya hay ${Formato.litros(dia.litros)}" +
                    (if (ganadero != null && delGanadero > 0) " (${ganadero.nombre}: ${Formato.litros(delGanadero)})" else "")
            }
        }
        selector = SelectorSemana(
            this, f,
            existente?.let { LocalDate.parse(Formato.soloFecha(it.fecha)) } ?: LocalDate.now()
        ) { mostrarDia() }
        mostrarDia()
        f.spGanadero.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = mostrarDia()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        f.btnNuevoGanadero.setOnClickListener {
            abrirFormularioGanadero(null) { nuevo ->
                if (nuevo != null) {
                    ganaderos = (ganaderos + nuevo).sortedBy { it.nombre.lowercase() }
                    llenarGanaderos(nuevo.id)
                }
            }
        }

        val titulo = if (existente == null) "Registrar leche" else "Corregir registro de leche"
        mostrarFormulario(titulo, f.root, foco = f.etLitros.takeIf { existente == null }) { dialogo ->
            val litros = Formato.leerNumero(f.etLitros.text)
            val ganadero = ganaderoElegido()
            if (litros == null || litros <= 0) {
                f.etLitros.error = "Escribe cuántos litros llegaron"
                return@mostrarFormulario false
            }
            if (ganadero == null) {
                toast(if (ganaderos.isEmpty()) "Primero agrega el ganadero con «+ Agregar ganadero»" else "Elige de qué ganadero viene la leche")
                return@mostrarFormulario false
            }
            val fecha = selector.dia.toString()
            val request = LecheRequest(
                litros = litros,
                ganaderoId = ganadero.id,
                observaciones = f.etObservaciones.text.toString().trim(),
                fecha = fecha
            )
            val id = existente?.id
            val cuando = Semana.diaLargo(selector.dia)
            if (id == null) {
                guardarConConfirmacion(dialogo, "Listo: ${Formato.litros(litros)} de ${ganadero.nombre} el $cuando", { confirmar ->
                    RetrofitClient.api.crearLeche(request.copy(observaciones = request.observaciones?.ifEmpty { null }, confirmar = confirmar.takeIf { it }))
                }) { cargar() }
            } else {
                guardarConConfirmacion(dialogo, "Registro corregido: ${Formato.litros(litros)} de ${ganadero.nombre}", { confirmar ->
                    RetrofitClient.api.actualizarLeche(id, request.copy(confirmar = confirmar.takeIf { it }))
                }) { cargar() }
            }
            false
        }
    }

    private fun eliminarLeche(l: Leche) {
        val id = l.id ?: return
        confirmar("Eliminar registro", "¿Eliminar ${Formato.litros(l.litros)} del ${Formato.fechaLegible(l.fecha)}?") {
            viewLifecycleOwner.lifecycleScope.launch {
                var r = llamarApi { RetrofitClient.api.eliminarLeche(id) }
                if (r is Resultado.Error && r.requiereConfirmacion && preguntar("Semana ya pagada", r.mensaje, "Eliminar igual")) {
                    r = llamarApi { RetrofitClient.api.eliminarLeche(id, confirmar = true) }
                }
                if (view == null) return@launch
                when (r) {
                    is Resultado.Exito -> { toast("Registro eliminado"); cargar() }
                    is Resultado.Error -> if (!r.requiereConfirmacion) manejarError(r)
                }
            }
        }
    }

    // ========================================
    // PRODUCCIÓN
    // ========================================

    /** [existente] = corregir una producción; null = registrar una nueva. */
    private fun abrirFormularioProduccion(existente: Produccion?) {
        // Se trae la producción reciente: al abrir desde el menú la lista aún no ha cargado
        conApi({ RetrofitClient.api.getProductos() }) { productos ->
            conApi({ RetrofitClient.api.getProduccion(limit = 60) }) { pr ->
                produccion = pr.orEmpty()
                mostrarFormularioProduccion(productos.orEmpty(), existente)
            }
        }
    }

    private fun mostrarFormularioProduccion(productos: List<Producto>, existente: Produccion?) {
        val f = DialogProduccionBinding.inflate(layoutInflater)
        var fecha = existente?.let { Formato.soloFecha(it.fecha) } ?: Formato.hoyIso()

        f.spProducto.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("— No sumar al inventario —") + productos.map { it.nombre }
        )
        // Por defecto, el producto de la producción (al corregir), el de la última o el único que haya
        val idProducto = if (existente != null) existente.productoId else produccion.firstOrNull()?.productoId
        val indice = productos.indexOfFirst { it.id == idProducto }
        when {
            indice >= 0 -> f.spProducto.setSelection(indice + 1)
            existente == null && productos.size == 1 -> f.spProducto.setSelection(1)
        }
        existente?.let {
            f.etLitros.setText(Formato.campo(it.litrosUsados))
            f.etKg.setText(Formato.campo(it.kgProducidos))
            f.etObservaciones.setText(it.observaciones.orEmpty())
        }

        // Leche del día elegido: recibida (de todos los ganaderos) y ya usada en otras producciones
        var recibidos: Double? = null
        var usadosEnOtras = 0.0
        var ultimaSugerencia: String? = null
        fun cargarLecheDelDia() {
            val pedida = fecha
            recibidos = null
            f.tvLecheDelDia.text = "Buscando la leche recibida ese día…"
            conApi({ RetrofitClient.api.getLecheDias(pedida, pedida) }) { dias ->
                if (pedida != fecha) return@conApi
                val dia = dias?.firstOrNull()
                val r = dia?.litros ?: 0.0
                // Al corregir, los litros de esta misma producción no cuentan como "ya usados"
                val propios = existente?.takeIf { Formato.soloFecha(it.fecha) == pedida }?.litrosUsados ?: 0.0
                usadosEnOtras = ((dia?.litrosUsados ?: 0.0) - propios).coerceAtLeast(0.0)
                recibidos = r
                val disponibles = (r - usadosEnOtras).coerceAtLeast(0.0)
                f.tvLecheDelDia.text = when {
                    r <= 0 -> "No hay leche registrada ese día"
                    usadosEnOtras > 0 -> "Leche recibida ese día: ${Formato.litros(r)} · ya usada en otra producción: " +
                        "${Formato.litros(usadosEnOtras)} · quedan ${Formato.litros(disponibles)}"
                    else -> "Leche recibida ese día: ${Formato.litros(r)} (de todos los ganaderos)"
                }
                // Producción nueva: se proponen los litros del día, salvo que el usuario ya haya escrito otro número
                val actual = f.etLitros.text.toString()
                if (existente == null && disponibles > 0 && (actual.isEmpty() || actual == ultimaSugerencia)) {
                    ultimaSugerencia = Formato.campo(disponibles)
                    f.etLitros.setText(ultimaSugerencia)
                }
            }
        }

        f.tvFecha.text = Formato.fechaLegible(fecha)
        f.tvFecha.setOnClickListener {
            elegirFecha(fecha) { fecha = it; f.tvFecha.text = Formato.fechaLegible(it); cargarLecheDelDia() }
        }
        cargarLecheDelDia()

        val calcular = {
            val litros = Formato.leerNumero(f.etLitros.text) ?: 0.0
            val kg = Formato.leerNumero(f.etKg.text) ?: 0.0
            f.tvRendimiento.text = if (litros > 0 && kg > 0)
                "Rendimiento: ${Formato.numero(librasPorLata(litros, kg))} lb por lata (${Formato.numero(litros / LITROS_POR_LATA, 1)} latas)" else ""
        }
        f.etLitros.doAfterTextChanged { calcular() }
        f.etKg.doAfterTextChanged { calcular() }
        calcular()

        val titulo = if (existente == null) "Registrar producción" else "Corregir producción"
        mostrarFormulario(titulo, f.root, foco = if (existente == null) f.etKg else null) { dialogo ->
            val litros = Formato.leerNumero(f.etLitros.text)
            val kg = Formato.leerNumero(f.etKg.text)
            if (litros == null || litros <= 0) {
                f.etLitros.error = "Escribe cuántos litros de leche usaste"
                return@mostrarFormulario false
            }
            if (kg == null || kg <= 0) {
                f.etKg.error = "Escribe cuántos kilos de queso salieron"
                return@mostrarFormulario false
            }
            val producto = productos.getOrNull(f.spProducto.selectedItemPosition - 1)
            val request = ProduccionRequest(
                litrosUsados = litros,
                kgProducidos = kg,
                observaciones = f.etObservaciones.text.toString().trim().ifEmpty { null },
                fecha = fecha,
                productoId = producto?.id
            )
            val guardar = {
                if (existente?.id == null) {
                    val mensaje = if (producto != null) "Listo: ${Formato.kg(kg)} de ${producto.nombre} sumados al inventario"
                    else "Listo: producción de ${Formato.kg(kg)} registrada"
                    guardarEnApi(dialogo, mensaje, { RetrofitClient.api.crearProduccion(request) }) { cargar() }
                } else {
                    // productoId "" = ya no se suma al inventario (null no cambiaría nada)
                    guardarEnApi(dialogo, "Producción corregida: ${Formato.kg(kg)}", {
                        RetrofitClient.api.actualizarProduccion(existente.id, request.copy(productoId = producto?.id ?: ""))
                    }) { cargar() }
                }
            }
            // Aviso si se usan más litros de los que llegaron ese día (puede ser leche de otro día: se permite)
            val r = recibidos
            val maximo = r?.let { it - usadosEnOtras }
            if (maximo != null && litros > maximo + 0.01) {
                val detalle = if (r <= 0) "No hay leche registrada el ${Formato.fechaLegible(fecha)}."
                else "El ${Formato.fechaLegible(fecha)} recibiste ${Formato.litros(r)}" +
                    (if (usadosEnOtras > 0) " y ya usaste ${Formato.litros(usadosEnOtras)} en otra producción" else "") +
                    ", pero vas a registrar ${Formato.litros(litros)}."
                confirmar("Más leche de la recibida", "$detalle\n¿Guardar de todos modos?", "Guardar igual") { guardar() }
            } else {
                guardar()
            }
            false
        }
    }

    private fun eliminarProduccion(p: Produccion) {
        val id = p.id ?: return
        confirmar(
            "Eliminar producción",
            "¿Eliminar la producción de ${Formato.kg(p.kgProducidos)} del ${Formato.fechaLegible(p.fecha)}?\n" +
                "Si se sumó al inventario, también se descontará."
        ) {
            conApi({ RetrofitClient.api.eliminarProduccion(id) }) {
                toast("Producción eliminada")
                cargar()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Una "lata" de leche son 20 litros. */
        const val LITROS_POR_LATA = 20.0

        const val ARG_MODO = "modo"
        const val MODO_PRODUCCION = "produccion"

        fun librasPorLata(litros: Double, kg: Double): Double =
            if (litros > 0) Formato.kgALibras(kg) / (litros / LITROS_POR_LATA) else 0.0
    }
}
