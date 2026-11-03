package com.queseria.app.ui.cuenta

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.queseria.app.R
import com.queseria.app.api.Resultado
import com.queseria.app.api.RetrofitClient
import com.queseria.app.api.llamarApi
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.FragmentPerfilBinding
import com.queseria.app.models.PerfilRequest
import com.queseria.app.models.Usuario
import com.queseria.app.ui.common.conApi
import com.queseria.app.ui.common.manejarError
import com.queseria.app.ui.common.mostrar
import com.queseria.app.ui.common.toast
import kotlinx.coroutines.launch

/**
 * Mi perfil: nombre del negocio (obligatorio), dueño, teléfono, correo, dirección y NIT.
 * El usuario con el que se inicia sesión se muestra pero no se cambia.
 */
class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvUsuario.text = SessionManager(requireContext()).obtenerUsername().orEmpty()
        binding.cabecera.mostrar("Mi cuenta", R.drawable.img_guia_quesos) { findNavController().navigateUp() }
        binding.btnCancelar.setOnClickListener { findNavController().navigateUp() }
        binding.swipeRefresh.setOnRefreshListener { cargar() }
        binding.btnGuardar.setOnClickListener { guardar() }
        cargar()
    }

    private fun cargar() {
        conApi({ RetrofitClient.api.perfil() }, binding.swipeRefresh) { u -> u?.let { mostrar(it) } }
    }

    private fun mostrar(u: Usuario) {
        binding.tvUsuario.text = u.username
        binding.etNombreNegocio.setText(u.nombreNegocio)
        binding.etPropietario.setText(u.propietario.orEmpty())
        binding.etTelefono.setText(u.telefono.orEmpty())
        binding.etEmail.setText(u.email.orEmpty())
        binding.etDireccion.setText(u.direccion.orEmpty())
        binding.etNit.setText(u.nit.orEmpty())
        // Hasta no tener los datos guardados no se deja guardar (no se borraría nada por error)
        binding.btnGuardar.isEnabled = true
    }

    private fun guardar() {
        val negocio = binding.etNombreNegocio.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        if (negocio.isEmpty()) {
            binding.etNombreNegocio.error = "Escribe el nombre del negocio"
            binding.etNombreNegocio.requestFocus()
            return
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "El correo no es válido"
            binding.etEmail.requestFocus()
            return
        }
        val request = PerfilRequest(
            nombreNegocio = negocio,
            propietario = binding.etPropietario.text.toString().trim(),
            telefono = binding.etTelefono.text.toString().trim(),
            email = email,
            direccion = binding.etDireccion.text.toString().trim(),
            nit = binding.etNit.text.toString().trim()
        )
        binding.btnGuardar.isEnabled = false
        binding.btnGuardar.text = "Guardando…"
        viewLifecycleOwner.lifecycleScope.launch {
            val r = llamarApi { RetrofitClient.api.actualizarPerfil(request) }
            if (view == null) return@launch
            binding.btnGuardar.isEnabled = true
            binding.btnGuardar.text = "Guardar"
            when (r) {
                is Resultado.Exito -> {
                    // Inicio y los PDF usan el nombre del negocio guardado en el teléfono
                    val u = r.data
                    val sesion = SessionManager(requireContext())
                    sesion.guardarDatosUsuario(u?.username ?: sesion.obtenerUsername().orEmpty(), u?.nombreNegocio ?: negocio)
                    toast("Listo: perfil guardado")
                    findNavController().navigateUp()
                }
                is Resultado.Error -> manejarError(r)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
