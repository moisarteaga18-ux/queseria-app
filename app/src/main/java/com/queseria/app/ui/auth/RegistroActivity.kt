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
import com.queseria.app.databinding.ActivityRegistroBinding
import com.queseria.app.models.RegistroRequest
import com.queseria.app.ui.common.mostrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de registro de cuenta nueva.
 * - Pide username, password, nombre del negocio y email
 * - Llama al backend POST /api/auth/registro
 * - Si el registro es exitoso, guarda el token y va a MainActivity
 * - Si falla, muestra el error al usuario
 */
class RegistroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding: conecta el XML con esta clase
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cabecera.mostrar("Crear cuenta", R.drawable.img_guia_paisaje_vaca_queso) { finish() }

        sessionManager = SessionManager(this)

        configurarListeners()
    }

    private fun configurarListeners() {
        // Botón "CREAR CUENTA"
        binding.btnRegistro.setOnClickListener {
            intentarRegistro()
        }

        // Texto "Inicia sesión" (vuelve al login)
        binding.tvIrALogin.setOnClickListener {
            finish() // simplemente cierra esta pantalla y vuelve a LoginActivity
        }
    }

    private fun intentarRegistro() {
        val nombreNegocio = binding.etNombreNegocio.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // ===== Validaciones =====
        if (nombreNegocio.isEmpty()) {
            mostrarMensaje("Ingresa el nombre de tu negocio")
            binding.etNombreNegocio.requestFocus()
            return
        }

        if (email.isEmpty()) {
            mostrarMensaje("Ingresa tu correo electrónico")
            binding.etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mostrarMensaje("El correo electrónico no es válido")
            binding.etEmail.requestFocus()
            return
        }

        if (username.isEmpty()) {
            mostrarMensaje("Ingresa un nombre de usuario")
            binding.etUsername.requestFocus()
            return
        }

        if (username.length < 3) {
            mostrarMensaje("El usuario debe tener al menos 3 caracteres")
            binding.etUsername.requestFocus()
            return
        }

        if (password.isEmpty()) {
            mostrarMensaje("Ingresa una contraseña")
            binding.etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            mostrarMensaje("La contraseña debe tener al menos 6 caracteres")
            binding.etPassword.requestFocus()
            return
        }

        // ===== Llamada al backend =====
        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.registro(
                        RegistroRequest(
                            username = username,
                            password = password,
                            nombreNegocio = nombreNegocio,
                            email = email
                        )
                    )
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.ok == true && body.data != null) {
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
                            mostrarMensaje("¡Cuenta creada exitosamente!")
                            irAMainActivity()
                        } else {
                            mostrarMensaje("Error: el servidor no devolvió un token")
                        }
                    } else {
                        mostrarMensaje(body?.error ?: "No se pudo crear la cuenta")
                    }
                } else {
                    when (response.code()) {
                        409 -> mostrarMensaje("Ese usuario o correo ya existe")
                        400 -> mostrarMensaje("Datos inválidos. Revisa los campos.")
                        else -> mostrarMensaje("Error del servidor (${response.code()})")
                    }
                }
            } catch (e: Exception) {
                mostrarMensaje("Error de conexión: ${e.message}")
            } finally {
                mostrarCargando(false)
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnRegistro.isEnabled = !cargando
        binding.etNombreNegocio.isEnabled = !cargando
        binding.etEmail.isEnabled = !cargando
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
}