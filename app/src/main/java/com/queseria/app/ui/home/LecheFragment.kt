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
import com.queseria.app.databinding.CabeceraLecheBinding
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
import com.queseria.app.ui.common.Tabla
import com.queseria.app.ui.common.dp
import com.queseria.app.ui.common.fechaCorta
import com.queseria.app.ui.common.marcaPendiente
import com.queseria.app.ui.common.ListaPorDias
import com.queseria.app.offline.Pendiente
import com.queseria.app.offline.Pendientes
import com.queseria.app.offline.comoLeche
import com.queseria.app.offline.comoProduccion
import com.queseria.app.offline.etiqueta
import com.queseria.app.offline.guardarOEnCola
import com.queseria.app.offline.mostrarPendiente
import com.queseria.app.offline.nuevoIdLocal
import com.queseria.app.offline.pendiente
import com.queseria.app.offline.pendientesDe
import com.queseria.app.ui.common.NavegadorSemana
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.confirmar
import com.queseria.app.ui.common.elegirFecha
import com.queseria.app.ui.common.guardarConConfirmacion
import com.queseria.app.ui.common.guardarEnApi
import com.queseria.app.ui.common.Cambio
import com.queseria.app.ui.common.Dato
import com.queseria.app.ui.common.borrarConDeshacer
import com.queseria.app.ui.common.confirmarBorrado
import com.queseria.app.ui.common.confirmarCambios
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
    private var cabeceraLeche: CabeceraLecheBinding? = null
    private var cabeceraProduccion: CabeceraProduccionBinding? = null
    private var navegador: NavegadorSemana? = null

    /** Leche de la semana elegida (la más nueva primero). */
    private var leche: List<Leche> = emptyList()
    /** Precio del litro de la semana elegida (null = no se ha definido). */
    private var precioLitro: Double? = null
    /** Producción reciente (para proponer el producto en el formulario). */
    private var produccion: List<Produccion> = emptyList()
    /** Producción de la semana elegida. */
    private var produccionSemana: List<Produccion> = emptyList()

    private val enLeche by lazy { arguments?.getString(ARG_MODO) != MODO_PRODUCCION }

    // Leche de la semana como tabla (viernes → jueves): Fecha (fija) | Ganadero | Litros | Valor
    private val adapterLeche = Tabla<Leche>(
        columnas = listOf(
            Tabla.Columna("Fecha", 84),
            Tabla.Columna("Ganadero", 118),
            Tabla.Columna("Litros", 80, numero = true),
            Tabla.Columna("Valor", 112, numero = true)
        ),
        celdas = { l ->
            listOf(
                fechaCorta(l.fecha),
                if (l.ganaderoId == null) "Sin ganadero" else l.nombreGanadero ?: "Ganadero",
                Formato.numero(l.litros, 1),
                precioLitro?.let { Formato.moneda(l.litros * it) } ?: "—"
            )
        },
        marca = { l -> if (l.pendiente == null && l.ganaderoId == null) Tabla.Marca.AVISO else marcaPendiente(l.pendiente) },
        nota = { l -> l.pendiente?.etiqueta ?: if (l.ganaderoId == null) "Asignar" else null },
        // Hecho sin internet y sin subir: se muestra su estado (no se corrige ni se borra aquí)
        alTocar = { l -> l.pendiente?.let { requireContext().mostrarPendiente(it) } ?: abrirFormularioLeche(l) }
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
                etiqueta = p.pendiente?.etiqueta ?: "${Formato.numero(librasPorLata(p.litrosUsados, p.kgProducidos))} lb/lata",
                colorEtiqueta = when {
                    p.pendiente?.rechazado == true -> R.color.rojo_error
                    p.pendiente != null -> R.color.ambar_advertencia
                    else -> R.color.azul_info
                },
                icono = R.drawable.ic_produccion,
                colorIcono = R.color.azul_info
            )
        },
        alTocar = { p -> p.pendiente?.let { requireContext().mostrarPendiente(it) } ?: abrirFormularioProduccion(p) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListaBinding.inflate(inflater, container, false)
        if (enLeche) cabeceraLeche = CabeceraLecheBinding.inflate(inflater, binding.contenedorCabecera, true)
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
        if (enLeche) {
            // Tabla con el encabezado fijo; semana de viernes a jueves (por defecto la actual)
            binding.rvLista.setPadding(dp(8), 0, dp(8), dp(96))
            adapterLeche.crearEncabezado(binding.contenedorFijo)
            navegador = NavegadorSemana(cabeceraLeche!!.semana) { mostrar(); cargar() }
            mostrar()
            cargar()
        } else {
            // Semana de viernes a jueves (por defecto la actual); cambiarla vuelve a cargar
            navegador = NavegadorSemana(cabeceraProduccion!!.semana) { mostrar(); cargar() }
            mostrar()
            cargar()
        }

        // Cuando se sube (o se rechaza) un registro hecho sin internet, la lista se actualiza
        var primera = true
        Pendientes.cambios.observe(viewLifecycleOwner) { if (primera) primera = false else cargar() }

        // Desde el menú de Inicio se abre el formulario directamente
        if (arguments?.getBoolean(InicioFragment.ARG_NUEVO) == true) {
            arguments?.remove(InicioFragment.ARG_NUEVO)
            if (enLeche) abrirFormularioLeche(null) else abrirFormularioProduccion(null)
        }
    }

    private fun cargar() {
        if (enLeche) {
            val semana = navegador?.semana ?: return
            // Precio del litro de la semana (columna Valor); sin precio la columna dice —
            // (404 = la semana no tiene precios: no es un error para mostrar)
            viewLifecycleOwner.lifecycleScope.launch {
                val r = llamarApi { RetrofitClient.api.getPrecioActual(semana.inicio.toString()) }
                if (view == null || semana != navegador?.semana) return@launch
                precioLitro = (r as? Resultado.Exito)?.data?.precioLitro?.takeIf { it > 0 }
                mostrar()
            }
            conApi({ RetrofitClient.api.getLeche(limit = 1000, desde = semana.inicio.toString(), hasta = semana.fin.toString()) }, binding.swipeRefresh) {
                if (semana != navegador?.semana) return@conApi
                val servidor = it.orEmpty()
                // Más los hechos sin internet que todavía no llegan (arriba, son los más nuevos)
                val locales = pendientesDe(Pendiente.LECHE, servidor.mapNotNull { l -> l.idLocal }.toSet()).map { p -> p.comoLeche() }
                    .filter { l -> semana.contiene(LocalDate.parse(Formato.soloFecha(l.fecha))) }
                leche = (locales + servidor).sortedByDescending { l -> Formato.soloFecha(l.fecha) }
                mostrar()
            }
        } else {
            // La ruta no filtra por fechas: se trae la producción reciente y se toma la de la semana elegida
            val semana = navegador?.semana ?: return
            conApi({ RetrofitClient.api.getProduccion(limit = 1000) }, binding.swipeRefresh) { lista ->
                if (semana != navegador?.semana) return@conApi
                val servidor = lista.orEmpty()
                val locales = pendientesDe(Pendiente.PRODUCCION, servidor.mapNotNull { p -> p.idLocal }.toSet()).map { p -> p.comoProduccion() }
                produccion = servidor
                produccionSemana = (servidor + locales).filter { semana.contiene(LocalDate.parse(Formato.soloFecha(it.fecha))) }
                mostrar()
            }
        }
    }

    private fun mostrar() {
        val cabecera = cabeceraLeche
        if (cabecera != null) {
            val semana = navegador?.semana ?: return
            val litros = leche.sumOf { it.litros }
            val litrosHoy = leche.filter { Formato.soloFecha(it.fecha) == Formato.hoyIso() }.sumOf { it.litros }
            val sinGanadero = leche.count { it.ganaderoId == null && it.pendiente == null }
            val precio = precioLitro
            cabecera.tvLitros.text = Formato.litros(litros)
            cabecera.tvLatas.text = "${Formato.numero(litros / LITROS_POR_LATA, 1)} latas en la semana"
            cabecera.tvValor.text = precio?.let { Formato.moneda(litros * it) } ?: "—"
            cabecera.tvValorDetalle.text = precio?.let { "a ${Formato.moneda(it)} el litro" } ?: "sin precio del litro"
            cabecera.tvHoy.text = if (semana.esActual) Formato.litros(litrosHoy) else "—"
            cabecera.tvAviso.text = "$sinGanadero ${if (sinGanadero == 1) "registro" else "registros"} sin ganadero: tócalos para asignarlo"
            cabecera.tvAviso.visibility = if (sinGanadero > 0) View.VISIBLE else View.GONE
            // Tabla del viernes al jueves; al final, los totales
            adapterLeche.mostrar(
                leche.sortedBy { Formato.soloFecha(it.fecha) },
                totales = listOf("Total", "${leche.size} ${if (leche.size == 1) "registro" else "registros"}",
                    Formato.numero(litros, 1), precio?.let { Formato.moneda(litros * it) } ?: "—"),
                vacio = "Sin leche registrada en esta semana.\nToca + Registrar leche para anotar la de hoy."
            )
            binding.tvVacio.visibility = View.GONE
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
        mostrarFormulario(titulo, f.root, foco = f.etLitros.takeIf { existente == null },
            alBorrar = existente?.let { { eliminarLeche(it) } }) { dialogo ->
            val (litros, errorLitros) = Formato.validarPositivo(f.etLitros.text, "Escribe cuántos litros llegaron", "Los litros", plural = true)
            val ganadero = ganaderoElegido()
            if (litros == null) {
                f.etLitros.error = errorLitros
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
                // Lleva idLocal: sin internet queda en el teléfono y al subirse no se duplica
                val nuevo = request.copy(observaciones = request.observaciones?.ifEmpty { null }, idLocal = nuevoIdLocal())
                val enCola = pendiente(Pendiente.LECHE, nuevo.idLocal!!, nuevo, fecha,
                    "${Formato.litros(litros)} de ${ganadero.nombre} el ${cuando.replaceFirstChar { it.lowercase() }}", ganadero.nombre)
                guardarOEnCola(dialogo, "Listo: ${Formato.litros(litros)} de ${ganadero.nombre} el $cuando", enCola, { confirmar ->
                    RetrofitClient.api.crearLeche(nuevo.copy(confirmar = confirmar.takeIf { it }))
                }) { cargar() }
            } else {
                // Antes de guardar se muestra qué cambia ("Litros: 40 → 45")
                confirmarCambios(listOf(
                    Cambio("Fecha", Semana.diaLargo(LocalDate.parse(Formato.soloFecha(existente.fecha))), cuando),
                    Cambio("Ganadero", existente.nombreGanadero ?: "Sin ganadero", ganadero.nombre),
                    Cambio("Litros", Formato.litros(existente.litros), Formato.litros(litros)),
                    Cambio("Nota", existente.observaciones.orEmpty(), request.observaciones.orEmpty())
                )) {
                    guardarConConfirmacion(dialogo, "Registro corregido: ${Formato.litros(litros)} de ${ganadero.nombre}", { confirmar ->
                        RetrofitClient.api.actualizarLeche(id, request.copy(confirmar = confirmar.takeIf { it }))
                    }) { cargar() }
                }
            }
            false
        }
    }

    /** Borrar leche: muestra el registro y pregunta; después, "Deshacer" unos segundos (queda en la Papelera). */
    private fun eliminarLeche(l: Leche) {
        val id = l.id ?: return
        confirmarBorrado("Borrar registro de leche", listOf(
            Dato("Fecha", Formato.fechaLegible(l.fecha)),
            Dato("Ganadero", l.nombreGanadero ?: "Sin ganadero"),
            Dato("Litros", Formato.litros(l.litros)),
            Dato("Valor", precioLitro?.let { Formato.moneda(l.litros * it) } ?: "—")
        )) {
            borrarConDeshacer("Registro de leche borrado", { confirmar ->
                RetrofitClient.api.eliminarLeche(id, confirmar = confirmar.takeIf { it })
            }) { cargar() }
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
        mostrarFormulario(titulo, f.root, foco = if (existente == null) f.etKg else null,
            alBorrar = existente?.let { { eliminarProduccion(it) } }) { dialogo ->
            val (litros, errorLitros) = Formato.validarPositivo(f.etLitros.text, "Escribe cuántos litros de leche usaste", "Los litros", plural = true)
            val (kg, errorKg) = Formato.validarPositivo(f.etKg.text, "Escribe cuántos kilos de queso salieron", "Los kilos", plural = true)
            if (litros == null) {
                f.etLitros.error = errorLitros
                return@mostrarFormulario false
            }
            if (kg == null) {
                f.etKg.error = errorKg
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
                    val nueva = request.copy(idLocal = nuevoIdLocal())
                    val enCola = pendiente(Pendiente.PRODUCCION, nueva.idLocal!!, nueva, fecha,
                        "producción de ${Formato.kg(kg)} del ${Formato.fechaLegible(fecha)}", producto?.nombre)
                    guardarOEnCola(dialogo, mensaje, enCola, { RetrofitClient.api.crearProduccion(nueva) }) { cargar() }
                } else {
                    // Antes de guardar se muestra qué cambia; productoId "" = ya no se suma al inventario
                    val productoAntes = productos.firstOrNull { it.id == existente.productoId }?.nombre ?: "No sumar al inventario"
                    confirmarCambios(listOf(
                        Cambio("Fecha", Formato.fechaLegible(existente.fecha), Formato.fechaLegible(fecha)),
                        Cambio("Litros", Formato.litros(existente.litrosUsados), Formato.litros(litros)),
                        Cambio("Queso", Formato.kg(existente.kgProducidos), Formato.kg(kg)),
                        Cambio("Producto", productoAntes, producto?.nombre ?: "No sumar al inventario"),
                        Cambio("Nota", existente.observaciones.orEmpty(), request.observaciones.orEmpty())
                    )) {
                        guardarEnApi(dialogo, "Producción corregida: ${Formato.kg(kg)}", {
                            RetrofitClient.api.actualizarProduccion(existente.id, request.copy(productoId = producto?.id ?: ""))
                        }) { cargar() }
                    }
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

    /** Borrar producción: muestra el registro y pregunta; después, "Deshacer" (queda en la Papelera). */
    private fun eliminarProduccion(p: Produccion) {
        val id = p.id ?: return
        confirmarBorrado("Borrar producción", listOf(
            Dato("Fecha", Formato.fechaLegible(p.fecha)),
            Dato("Leche", Formato.litros(p.litrosUsados)),
            Dato("Queso", Formato.kg(p.kgProducidos)),
            Dato("Rendimiento", "${Formato.numero(librasPorLata(p.litrosUsados, p.kgProducidos))} lb por lata")
        ), aviso = if (p.productoId != null) "Ese queso también se quitará del inventario." else null) {
            borrarConDeshacer("Producción borrada", { RetrofitClient.api.eliminarProduccion(id) }) { cargar() }
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
