package com.queseria.app.api

import android.content.Context
import com.queseria.app.BuildConfig
import com.queseria.app.offline.CacheInterceptor
import okhttp3.Request
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // URL del backend (Render por defecto; ver app/build.gradle)
    private val BASE_URL = BuildConfig.API_URL

    // Variable que guarda el token JWT del usuario logueado
    // Se establece desde LoginActivity y se usa automáticamente en cada petición
    @Volatile
    var authToken: String? = null

    // Interceptor que añade el header Authorization: Bearer <token>
    // automáticamente a todas las peticiones que requieran autenticación
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = if (authToken != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $authToken")
                .build()
        } else {
            originalRequest
        }
        chain.proceed(newRequest)
    }

    // Logger para ver en Logcat qué peticiones se hacen y qué respuesta llega
    // Solo en debug: en la versión final no se registran datos
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    /** Contexto de la app (para la carpeta de consultas guardadas); se fija en QuesoDataApp. */
    @Volatile
    private var contexto: Context? = null

    fun iniciar(context: Context) {
        contexto = context.applicationContext
    }

    // Consultas guardadas en el teléfono para verlas sin internet (ver offline/Conexion.kt). Va primero
    // para envolver a los demás: si la conexión falla, responde con lo guardado.
    private val cacheInterceptor = CacheInterceptor { CacheInterceptor.carpeta(contexto!!) }

    // Cliente HTTP con timeouts y los interceptores configurados. Conectar: 20 s (sin internet no se
    // espera un minuto); leer: 60 s (Render gratis tarda en despertar).
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(cacheInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Instancia de Retrofit que se crea solo cuando se necesita (lazy)
    // Esto convierte la interface QueseriaApi en un objeto que sí hace las llamadas HTTP
    /** Pregunta al servidor si responde (GET / del backend, sin datos). true = hay conexión. */
    fun probar(): Boolean = try {
        val raiz = BASE_URL.removeSuffix("/").removeSuffix("api")
        httpClient.newCall(Request.Builder().url(raiz).build()).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    val api: QueseriaApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QueseriaApi::class.java)
    }
}