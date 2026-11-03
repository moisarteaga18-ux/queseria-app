package com.queseria.app.ui.cuenta

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.FragmentContrasenaBinding
import com.queseria.app.models.CambiarContrasenaRequest
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.common.mostrar
import com.queseria.app.ui.common.toast
import kotlinx.coroutines.launch

/**
 * Cambiar contraseña: pide la actual y la nueva dos veces. El servidor devuelve un token nuevo
 * (este teléfono sigue con la sesión abierta) y las sesiones de otros teléfonos dejan de valer.
 */
class ContrasenaFragment : Fragment() {

    private var _binding: FragmentContrasenaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentContrasenaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val campos = listOf(binding.etActual, binding.etNueva, binding.etRepetir)
        binding.cbMostrar.setOnCheckedChangeListener { _, mostrar -> campos.forEach { mostrarTexto(it, mostrar) } }
        binding.cabecera.mostrar("Cambiar contraseña", R.drawable.img_guia_quesos) { findNavController().navigateUp() }
        binding.btnCancelar.setOnClickListener { findNavController().navigateUp() }
        binding.btnGuardar.setOnClickListener { guardar() }
    }

    private fun mostrarTexto(campo: EditText, mostrar: Boolean) {
        val cursor = campo.selectionEnd
        campo.inputType = InputType.TYPE_CLASS_TEXT or
            if (mostrar) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
        campo.setSelection(cursor.coerceIn(0, campo.length()))
    }

    private fun guardar() {
        val actual = binding.etActual.text.toString()
        val nueva = binding.etNueva.text.toString()
        val repetir = binding.etRepetir.text.toString()
        when {
            actual.isEmpty() -> return error(binding.etActual, "Escribe tu contraseña actual")
            nueva.length < 6 -> return error(binding.etNueva, "La contraseña nueva debe tener al menos 6 caracteres")
            nueva == actual -> return error(binding.etNueva, "Debe ser distinta de la actual")
            repetir != nueva -> return error(binding.etRepetir, "No coincide con la contraseña nueva")
        }

        binding.btnGuardar.isEnabled = false
        binding.btnGuardar.text = "Cambiando…"
        viewLifecycleOwner.lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.cambiarContrasena(CambiarContrasenaRequest(actual, nueva)) }
            if (view == null) return@launch
            binding.btnGuardar.isEnabled = true
            binding.btnGuardar.text = "Cambiar"
            when (r) {
                is Resultado.Exito -> {
                    // El token anterior ya no vale: se guarda el nuevo para seguir dentro
                    r.data?.token?.let { SessionManager(requireContext()).guardarToken(it) }
                    toast("Listo: contraseña cambiada")
                    findNavController().navigateUp()
                }
                is Resultado.Error -> {
                    manejarError(r)
                    if (r.mensaje.contains("actual", ignoreCase = true)) {
                        binding.etActual.text.clear()
                        binding.etActual.requestFocus()
                    }
                }
            }
        }
    }

    private fun error(campo: EditText, mensaje: String) {
        campo.error = mensaje
        campo.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
