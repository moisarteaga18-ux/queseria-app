package com.queseria.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.ActivityMainBinding
import com.queseria.app.ui.auth.LoginActivity
import com.queseria.app.util.Tema

/**
 * Pantalla principal de la app.
 * Contiene:
 * - Toolbar superior: título de la pantalla, flecha "atrás", casita para volver a Inicio
 *   y menú ⋮ con Cerrar sesión
 * - Contenedor de fragments (NavHost). Inicio es el menú principal.
 * - Barra inferior (guía de diseño): Inicio, Reportes, Ventas y Más (las demás pantallas y Configuración).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var navController: NavController
    private lateinit var appBarConfig: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Si por alguna razón llegamos a MainActivity sin sesión, regresar a Login
        if (!sessionManager.haySesionActiva()) {
            irALogin()
            return
        }

        // Cargar el token JWT en RetrofitClient para que las peticiones funcionen
        sessionManager.cargarTokenEnRetrofit()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Solo Inicio es de "nivel superior": las demás pantallas muestran la flecha atrás
        appBarConfig = AppBarConfiguration(setOf(R.id.nav_inicio))
        setSupportActionBar(binding.toolbar)
        setupActionBarWithNavController(navController, appBarConfig)

        // La casita se oculta en Inicio; Inicio tampoco muestra la barra (tiene su propia cabecera)
        navController.addOnDestinationChangedListener { _, destino, _ ->
            // Inicio y Mi cuenta / Cambiar contraseña traen su propia cabecera (con botón atrás)
            val cabeceraPropia = destino.id in setOf(R.id.nav_inicio, R.id.nav_perfil, R.id.nav_contrasena)
            binding.toolbar.visibility = if (cabeceraPropia) View.GONE else View.VISIBLE
            marcarBarra(destino.id)
            invalidateOptionsMenu()
        }

        binding.barraInferior.navInicio.setOnClickListener { irAInicio() }
        binding.barraInferior.navReportes.setOnClickListener { ir(R.id.nav_reportes) }
        binding.barraInferior.navVentas.setOnClickListener { ir(R.id.nav_ventas) }
        binding.barraInferior.navMas.setOnClickListener { abrirMas() }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val enInicio = ::navController.isInitialized && navController.currentDestination?.id == R.id.nav_inicio
        menu.findItem(R.id.action_inicio)?.isVisible = !enInicio
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_inicio -> {
                irAInicio()
                true
            }
            R.id.action_cerrar_sesion -> {
                confirmarCerrarSesion()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Pone en amarillo (con su rayita) la opción de la barra inferior de la pantalla [destino]. */
    private fun marcarBarra(destino: Int) {
        val b = binding.barraInferior
        val activa = when (destino) {
            R.id.nav_inicio -> 0
            R.id.nav_reportes, R.id.nav_ganancias, R.id.nav_rendimiento -> 1
            R.id.nav_ventas -> 2
            else -> 3
        }
        val opciones = listOf(
            Triple(b.icInicio, b.txtInicio, b.rayaInicio),
            Triple(b.icReportes, b.txtReportes, b.rayaReportes),
            Triple(b.icVentas, b.txtVentas, b.rayaVentas),
            Triple(b.icMas, b.txtMas, b.rayaMas)
        )
        val amarillo = ContextCompat.getColor(this, R.color.dorado_titulo)
        val gris = ContextCompat.getColor(this, R.color.qd_nav_inactivo)
        opciones.forEachIndexed { i, (icono, texto, raya) ->
            val color = if (i == activa) amarillo else gris
            icono.setColorFilter(color)
            texto.setTextColor(color)
            raya.visibility = if (i == activa) View.VISIBLE else View.INVISIBLE
        }
    }

    /** Abre una pantalla desde la barra inferior (sin apilar la misma pantalla dos veces). */
    private fun ir(destino: Int) {
        if (navController.currentDestination?.id == destino) return
        navController.navigate(destino, null, NavOptions.Builder().setLaunchSingleTop(true).build())
    }

    /** "Más" de la barra inferior: las pantallas que no están en la barra y Configuración. */
    private fun abrirMas() {
        val opciones = listOf<Pair<String, () -> Unit>>(
            "Inventario" to { ir(R.id.nav_inventario) },
            "Liquidación a ganaderos" to { ir(R.id.nav_liquidaciones) },
            "Precios" to { ir(R.id.nav_precios) },
            "Ganaderos" to { ir(R.id.nav_ganaderos) },
            "Clientes" to { ir(R.id.nav_clientes) },
            "Productos" to { ir(R.id.nav_productos) },
            "Mi cuenta" to { ir(R.id.nav_perfil) },
            "Cambiar contraseña" to { ir(R.id.nav_contrasena) },
            "Apariencia" to { elegirTema() },
            "Cerrar sesión" to { confirmarCerrarSesion() }
        )
        AlertDialog.Builder(this)
            .setTitle("Más")
            .setItems(opciones.map { it.first }.toTypedArray()) { _, i -> opciones[i].second() }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** Configuración: modo claro, oscuro o el que tenga el teléfono. Se aplica al instante. */
    fun elegirTema() {
        val modos = listOf(Tema.CLARO, Tema.OSCURO, Tema.SISTEMA)
        val nombres = arrayOf("Modo claro", "Modo oscuro", "Igual que el teléfono")
        val actual = modos.indexOf(Tema.actual(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Apariencia")
            .setSingleChoiceItems(nombres, actual) { dialogo, i ->
                dialogo.dismiss()
                if (i != actual) Tema.cambiar(this, modos[i])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Vuelve al menú de Inicio cerrando todas las pantallas abiertas encima. */
    private fun irAInicio() {
        if (!navController.popBackStack(R.id.nav_inicio, false)) navController.navigate(R.id.nav_inicio)
    }

    /**
     * Muestra un diálogo de confirmación antes de cerrar sesión (también desde el menú de la cabecera de Inicio).
     */
    fun confirmarCerrarSesion() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setPositiveButton("Sí, cerrar") { _, _ ->
                sessionManager.cerrarSesion()
                irALogin()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun irALogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
