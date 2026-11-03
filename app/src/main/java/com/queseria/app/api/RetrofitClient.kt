package com.queseria.app.api

import com.queseria.app.BuildConfig
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

    // Cliente HTTP con timeouts y los interceptores configurados
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Instancia de Retrofit que se crea solo cuando se necesita (lazy)
    // Esto convierte la interface QueseriaApi en un objeto que sí hace las llamadas HTTP
    val api: QueseriaApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QueseriaApi::class.java)
    }
}