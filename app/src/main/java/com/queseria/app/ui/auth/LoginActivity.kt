package com.queseria.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.queseria.app.MainActivity
import com.queseria.app.R
import com.queseria.app.api.RetrofitClient
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.ActivityLoginBinding
import com.queseria.app.models.LoginRequest
import com.queseria.app.ui.common.mostrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de inicio de sesión.
 * - Permite al usuario ingresar con username y contraseña
 * - Llama al backend POST /api/auth/login
 * - Si el login es exitoso, guarda el token JWT y va a MainActivity
 * - Si falla, muestra el error al usuario
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding: conecta el XML con esta clase
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cabecera.mostrar("Iniciar sesión", R.drawable.img_guia_quesos) {}
        binding.cabecera.btnAtras.visibility = View.GONE

        sessionManager = SessionManager(this)

        // Si el usuario ya tiene sesión activa, ir directo a MainActivity
        if (sessionManager.haySesionActiva()) {
            sessionManager.cargarTokenEnRetrofit()
            irAMainActivity()
            return
        }

        // Configurar listeners de los botones y enlaces
        configurarListeners()
    }

    private fun configurarListeners() {
        // Botón "INICIAR SESIÓN"
        binding.btnLogin.setOnClickListener {
            intentarLogin()
        }

        // Texto "Regístrate" (lleva a la pantalla de registro)
        binding.tvIrARegistro.setOnClickListener {
            irARegistro()
        }

        // "¿Olvidaste tu contraseña?" (lleva el usuario ya escrito, si lo hay)
        binding.tvOlvide.setOnClickListener {
            val intent = Intent(this, RecuperarActivity::class.java)
            intent.putExtra(RecuperarActivity.EXTRA_USUARIO, binding.etUsername.text.toString().trim())
            startActivity(intent)
        }
    }

    private fun intentarLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Validaciones básicas antes de llamar al backend
        if (username.isEmpty()) {
            mostrarMensaje("Por favor ingresa tu usuario")
            binding.etUsername.requestFocus()
            return
        }

        if (password.isEmpty()) {
            mostrarMensaje("Por favor ingresa tu contraseña")
            binding.etPassword.requestFocus()
            return
        }

        // Mostrar loading y deshabilitar botón
        mostrarCargando(true)

        // Llamada al backend dentro de una corrutina
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.login(LoginRequest(username, password))
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.ok == true && body.data != null) {
                        // Login exitoso: guardar token y datos del usuario
                        val token = body.data.token
                        val usuario = body.data.usuario

                        if (token != null) {
                            sessionManager.guardarToken(token)
                            if (usuario != null) {
                                sessionManager.guardarDatosUsuario(
                                    usuario.username,
                                    usuario.nombreNegocio
                                )
                            }
                            mostrarMensaje("¡Bienvenido!")
                            irAMainActivity()
                        } else {
                            mostrarMensaje("Error: el servidor no devolvió un token")
                        }
                    } else {
                        // El servidor respondió 200 pero ok=false
                        mostrarMensaje(body?.error ?: "Error desconocido")
                    }
                } else {
                    // Códigos 401, 500, etc.
                    when (response.code()) {
                        401 -> mostrarMensaje("Usuario o contraseña incorrectos")
                        404 -> mostrarMensaje("Servidor no disponible")
                        else -> mostrarMensaje("Error del servidor (${response.code()})")
                    }
                }
            } catch (e: Exception) {
                // Errores de red, timeout, sin internet, etc.
                mostrarMensaje("Error de conexión: ${e.message}")
            } finally {
                mostrarCargando(false)
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !cargando
        binding.etUsername.isEnabled = !cargando
        binding.etPassword.isEnabled = !cargando
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }

    private fun irAMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun irARegistro() {
        val intent = Intent(this, RegistroActivity::class.java)
        startActivity(intent)
    }
}