package com.queseria.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.queseria.app.MainActivity
import com.queseria.app.auth.SessionManager
import com.queseria.app.databinding.ActivitySplashBinding

/**
 * Pantalla de carga: muestra el logo completo de QuesoData un momento y luego
 * va a la pantalla principal (si hay sesión) o al login.
 * En Android 12+ el sistema muestra antes el ícono de la app (Theme.QuesoData.Splash).
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivLogo.scaleX = 0.85f
        binding.ivLogo.scaleY = 0.85f
        binding.ivLogo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(550)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
        binding.progreso.animate().alpha(1f).setStartDelay(500).setDuration(300).start()

        binding.root.postDelayed({ continuar() }, DURACION_MS)
    }

    private fun continuar() {
        if (isFinishing) return
        val sesion = SessionManager(this)
        val destino = if (sesion.haySesionActiva()) {
            sesion.cargarTokenEnRetrofit()
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destino))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    companion object {
        private const val DURACION_MS = 1400L
    }
}
