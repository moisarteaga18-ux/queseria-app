package com.queseria.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.queseria.app.MainActivity
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.ActivityRecuperarBinding
import com.queseria.app.models.RecuperarRequest
import com.queseria.app.models.RestablecerRequest
import com.queseria.app.ui.common.mostrar
import kotlinx.coroutines.launch

/**
 * Recuperar la contraseña con un código que llega al correo del perfil:
 * 1) se escribe el usuario y se pide el código (POST /api/auth/recuperar);
 * 2) se escribe el código y la contraseña nueva (POST /api/auth/restablecer) y se entra a la app.
 */
class RecuperarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecuperarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecuperarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cabecera.mostrar("Recuperar contraseña", R.drawable.img_guia_quesos) { finish() }

        intent.getStringExtra(EXTRA_USUARIO)?.let { binding.etUsername.setText(it) }
        binding.btnEnviar.setOnClickListener { enviarCodigo() }
        binding.tvReenviar.setOnClickListener { enviarCodigo() }
        binding.btnCambiar.setOnClickListener { cambiar() }
        binding.tvVolver.setOnClickListener { finish() }
        val campos = listOf(binding.etNueva, binding.etRepetir)
        binding.cbMostrar.setOnCheckedChangeListener { _, mostrar -> campos.forEach { mostrarTexto(it, mostrar) } }
    }

    private fun usuario() = binding.etUsername.text.toString().trim()

    private fun enviarCodigo() {
        val usuario = usuario()
        if (usuario.isEmpty()) {
            binding.etUsername.error = "Escribe tu usuario"
            binding.etUsername.requestFocus()
            return
        }
        cargando(true)
        lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.recuperar(RecuperarRequest(usuario)) }
            cargando(false)
            when (r) {
                is Resultado.Exito -> {
                    // Paso 2: el usuario queda fijo para que el código corresponda a esa cuenta
                    binding.etUsername.isEnabled = false
                    binding.btnEnviar.visibility = View.GONE
                    binding.grupoCodigo.visibility = View.VISIBLE
                    binding.tvSubtitulo.text = "Escribe el código y tu contraseña nueva"
                    binding.tvEnviado.text = (r.data?.mensaje ?: "Te enviamos un código.") +
                        "\nVence en ${r.data?.minutos ?: 15} minutos. Si no llega, revisa que el usuario esté bien " +
                        "escrito y que tu perfil tenga correo."
                    binding.etCodigo.text.clear()
                    binding.etCodigo.requestFocus()
                }
                is Resultado.Error -> mensaje(r.mensaje)
            }
        }
    }

    private fun cambiar() {
        val codigo = binding.etCodigo.text.toString().trim()
        val nueva = binding.etNueva.text.toString()
        val repetir = binding.etRepetir.text.toString()
        when {
            codigo.length != 6 -> return error(binding.etCodigo, "El código tiene 6 números")
            nueva.length < 6 -> return error(binding.etNueva, "La contraseña nueva debe tener al menos 6 caracteres")
            repetir != nueva -> return error(binding.etRepetir, "No coincide con la contraseña nueva")
        }
        cargando(true)
        lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.restablecer(RestablecerRequest(usuario(), codigo, nueva)) }
            cargando(false)
            when (r) {
                is Resultado.Exito -> {
                    val token = r.data?.token
                    if (token == null) {
                        mensaje("Contraseña cambiada. Inicia sesión con la nueva.")
                        finish()
                        return@launch
                    }
                    val sesion = SessionManager(this@RecuperarActivity)
                    sesion.guardarToken(token)
                    r.data.usuario?.let { sesion.guardarDatosUsuario(it.username, it.nombreNegocio) }
                    mensaje("Listo: contraseña cambiada")
                    val intent = Intent(this@RecuperarActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                is Resultado.Error -> {
                    mensaje(r.mensaje)
                    binding.etCodigo.text.clear()
                    binding.etCodigo.requestFocus()
                }
            }
        }
    }

    private fun mostrarTexto(campo: EditText, mostrar: Boolean) {
        val cursor = campo.selectionEnd
        campo.inputType = InputType.TYPE_CLASS_TEXT or
            if (mostrar) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
        campo.setSelection(cursor.coerceIn(0, campo.length()))
    }

    private fun cargando(si: Boolean) {
        binding.progressBar.visibility = if (si) View.VISIBLE else View.GONE
        binding.btnEnviar.isEnabled = !si
        binding.btnCambiar.isEnabled = !si
        binding.tvReenviar.isEnabled = !si
    }

    private fun error(campo: EditText, texto: String) {
        campo.error = texto
        campo.requestFocus()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_USUARIO = "usuario"
    }
}
