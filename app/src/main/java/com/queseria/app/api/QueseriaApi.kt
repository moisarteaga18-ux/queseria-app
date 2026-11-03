package com.queseria.app.api

import com.queseria.app.models.*
import com.queseria.app.util.Formato
import retrofit2.Response
import retrofit2.http.*

interface QueseriaApi {

    // ========================================
    // AUTENTICACIÓN (públicos, NO requieren token)
    // ========================================

    @POST("auth/registro")
    suspend fun registro(@Body request: RegistroRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    /** Envía un código al correo del usuario (responde igual aunque el usuario no exista). */
    @POST("auth/recuperar")
    suspend fun recuperar(@Body request: RecuperarRequest): Response<ApiResponse<RecuperarResponse>>

    /** Cambia la contraseña con el código del correo y devuelve una sesión nueva. */
    @POST("auth/restablecer")
    suspend fun restablecer(@Body request: RestablecerRequest): Response<ApiResponse<AuthResponse>>

    // Mi cuenta (requieren token)
    @GET("auth/perfil")
    suspend fun perfil(): Response<ApiResponse<Usuario>>

    @PUT("auth/perfil")
    suspend fun actualizarPerfil(@Body request: PerfilRequest): Response<ApiResponse<Usuario>>

    /** Devuelve un token nuevo: los anteriores (otros teléfonos) dejan de valer. */
    @PUT("auth/contrasena")
    suspend fun cambiarContrasena(@Body request: CambiarContrasenaRequest): Response<ApiResponse<AuthResponse>>

    // ========================================
    // LECHE
    // ========================================

    @GET("leche")
    suspend fun getLeche(
        @Query("limit") limit: Int = 30,
        @Query("fecha") fecha: String? = null
    ): Response<ApiResponse<List<Leche>>>

    /** Litros recibidos y usados por día entre [desde] y [hasta] (yyyy-MM-dd, incluidos). */
    @GET("leche/dias")
    suspend fun getLecheDias(
        @Query("desde") desde: String,
        @Query("hasta") hasta: String
    ): Response<ApiResponse<List<DiaLeche>>>

    @POST("leche")
    suspend fun crearLeche(@Body request: LecheRequest): Response<ApiResponse<Leche>>

    /** Corrige un registro. En una semana ya pagada responde 409 hasta que se mande confirmar = true. */
    @PUT("leche/{id}")
    suspend fun actualizarLeche(@Path("id") id: String, @Body request: LecheRequest): Response<ApiResponse<Leche>>

    @DELETE("leche/{id}")
    suspend fun eliminarLeche(
        @Path("id") id: String,
        @Query("confirmar") confirmar: Boolean? = null
    ): Response<ApiResponse<Any>>

    // ========================================
    // PRODUCCIÓN
    // ========================================

    @GET("produccion")
    suspend fun getProduccion(
        @Query("limit") limit: Int = 30
    ): Response<ApiResponse<List<Produccion>>>

    @POST("produccion")
    suspend fun crearProduccion(@Body request: ProduccionRequest): Response<ApiResponse<Produccion>>

    /** Corrige una producción (el backend ajusta el inventario y el rendimiento). */
    @PUT("produccion/{id}")
    suspend fun actualizarProduccion(@Path("id") id: String, @Body request: ProduccionRequest): Response<ApiResponse<Produccion>>

    @DELETE("produccion/{id}")
    suspend fun eliminarProduccion(@Path("id") id: String): Response<ApiResponse<Any>>

    // ========================================
    // PRECIOS
    // ========================================

    /** Precios (queso y litro de leche) de la semana que contiene [fecha] (por defecto, hoy). 404 si no hay. */
    @GET("precios/actual")
    suspend fun getPrecioActual(@Query("fecha") fecha: String = Formato.hoyIso()): Response<ApiResponse<Precio>>

    @GET("precios")
    suspend fun getPrecios(): Response<ApiResponse<List<Precio>>>

    /** Crea o corrige los precios de una semana (409 si cambia el litro de una semana ya pagada). */
    @PUT("precios/semana")
    suspend fun guardarPrecioSemana(@Body request: PrecioSemanaRequest): Response<ApiResponse<Precio>>

    // ========================================
    // VENTAS
    // ========================================

    @GET("ventas")
    suspend fun getVentas(
        @Query("limit") limit: Int = 50,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null
    ): Response<ApiResponse<List<Venta>>>

    @POST("ventas")
    suspend fun crearVenta(@Body request: VentaRequest): Response<ApiResponse<Venta>>

    /** Corrige una venta (el backend ajusta el inventario; 400 si no alcanza el stock). */
    @PUT("ventas/{id}")
    suspend fun actualizarVenta(@Path("id") id: String, @Body request: VentaRequest): Response<ApiResponse<Venta>>

    @DELETE("ventas/{id}")
    suspend fun eliminarVenta(@Path("id") id: String): Response<ApiResponse<Any>>

    // ========================================
    // RESUMEN / GANANCIAS
    // ========================================

    /** [fecha] es el día local del teléfono (yyyy-MM-dd), para no depender de la zona horaria del servidor. */
    @GET("resumen/hoy")
    suspend fun getResumenHoy(@Query("fecha") fecha: String): Response<ApiResponse<ResumenHoy>>

    /** Semana actual (viernes a jueves, según [fecha]) contra la anterior. */
    @GET("resumen/ganancias")
    suspend fun getGanancias(@Query("fecha") fecha: String = Formato.hoyIso()): Response<ApiResponse<Ganancias>>

    @GET("resumen/semanas")
    suspend fun getResumenSemanas(
        @Query("n") n: Int = 8,
        @Query("fecha") fecha: String? = null
    ): Response<ApiResponse<List<ResumenSemana>>>

    // ========================================
    // RENDIMIENTO
    // ========================================

    @GET("rendimiento/semanal")
    suspend fun getRendimientoSemanal(): Response<ApiResponse<RendimientoSemanal>>

    // ========================================
    // PRODUCTOS (tipos de queso)
    // ========================================

    @GET("productos")
    suspend fun getProductos(): Response<ApiResponse<List<Producto>>>

    @POST("productos")
    suspend fun crearProducto(@Body request: ProductoRequest): Response<ApiResponse<Producto>>

    @PUT("productos/{id}")
    suspend fun actualizarProducto(@Path("id") id: String, @Body request: ProductoRequest): Response<ApiResponse<Producto>>

    @DELETE("productos/{id}")
    suspend fun eliminarProducto(@Path("id") id: String): Response<ApiResponse<Any>>

    // ========================================
    // INVENTARIO
    // ========================================

    @GET("inventario/movimientos")
    suspend fun getMovimientos(@Query("limit") limit: Int = 100): Response<ApiResponse<List<MovimientoInventario>>>

    /** Devuelve el producto con el stock actualizado. */
    @POST("inventario/movimientos")
    suspend fun crearMovimiento(@Body request: MovimientoRequest): Response<ApiResponse<Producto>>

    /** Solo movimientos hechos a mano (los de ventas y producción se corrigen desde allí). */
    @PUT("inventario/movimientos/{id}")
    suspend fun actualizarMovimiento(@Path("id") id: String, @Body request: MovimientoRequest): Response<ApiResponse<Producto>>

    @DELETE("inventario/movimientos/{id}")
    suspend fun eliminarMovimiento(@Path("id") id: String): Response<ApiResponse<Producto>>

    // ========================================
    // CLIENTES
    // ========================================

    @GET("clientes")
    suspend fun getClientes(): Response<ApiResponse<List<Cliente>>>

    @GET("clientes/{id}")
    suspend fun getCliente(@Path("id") id: String): Response<ApiResponse<Cliente>>

    @GET("clientes/{id}/ventas")
    suspend fun getVentasCliente(@Path("id") id: String): Response<ApiResponse<List<Venta>>>

    @POST("clientes")
    suspend fun crearCliente(@Body cliente: Cliente): Response<ApiResponse<Cliente>>

    @PUT("clientes/{id}")
    suspend fun actualizarCliente(@Path("id") id: String, @Body cliente: Cliente): Response<ApiResponse<Cliente>>

    @DELETE("clientes/{id}")
    suspend fun eliminarCliente(@Path("id") id: String): Response<ApiResponse<Any>>

    // ========================================
    // GANADEROS
    // ========================================

    @GET("ganaderos")
    suspend fun getGanaderos(): Response<ApiResponse<List<Ganadero>>>

    @POST("ganaderos")
    suspend fun crearGanadero(@Body request: GanaderoRequest): Response<ApiResponse<Ganadero>>

    @PUT("ganaderos/{id}")
    suspend fun actualizarGanadero(@Path("id") id: String, @Body request: GanaderoRequest): Response<ApiResponse<Ganadero>>

    /** Si el ganadero ya tiene leche o pagos, el backend lo archiva en lugar de borrarlo. */
    @DELETE("ganaderos/{id}")
    suspend fun eliminarGanadero(@Path("id") id: String): Response<ApiResponse<EliminarGanaderoResponse>>

    // ========================================
    // LIQUIDACIONES A GANADEROS (semana de viernes a jueves)
    // ========================================

    /**
     * Resumen de la semana por ganadero. [fecha] es el día local: si la semana ya cerró
     * (pasó el jueves), el backend genera las liquidaciones que falten.
     */
    @GET("liquidaciones/semana")
    suspend fun getLiquidacionesSemana(
        @Query("semana") semana: Int,
        @Query("anio") anio: Int,
        @Query("fecha") fecha: String = Formato.hoyIso()
    ): Response<ApiResponse<LiquidacionesSemana>>

    /** La liquidación guardada o, si no existe, la calculada (estado "sin_generar"). */
    @GET("liquidaciones/previa")
    suspend fun getLiquidacionPrevia(
        @Query("ganaderoId") ganaderoId: String,
        @Query("semana") semana: Int,
        @Query("anio") anio: Int
    ): Response<ApiResponse<Liquidacion>>

    /** Resumen de las últimas [n] semanas (la actual primero): litros, total, pagado y pendiente. */
    @GET("liquidaciones/semanas")
    suspend fun getSemanasLiquidacion(
        @Query("n") n: Int = 12,
        @Query("fecha") fecha: String = Formato.hoyIso()
    ): Response<ApiResponse<List<SemanaLiquidacion>>>

    @GET("liquidaciones/resumen")
    suspend fun getResumenLiquidaciones(): Response<ApiResponse<ResumenPendientes>>

    @PUT("liquidaciones/precio")
    suspend fun fijarPrecioLeche(@Body request: PrecioLecheRequest): Response<ApiResponse<Any>>

    @POST("liquidaciones")
    suspend fun generarLiquidacion(@Body request: GenerarLiquidacionRequest): Response<ApiResponse<Liquidacion>>

    @POST("liquidaciones/generar-semana")
    suspend fun generarLiquidacionesSemana(@Body request: SemanaRequest): Response<ApiResponse<List<Liquidacion>>>

    @GET("liquidaciones")
    suspend fun getLiquidaciones(
        @Query("ganaderoId") ganaderoId: String? = null,
        @Query("limit") limit: Int = 100
    ): Response<ApiResponse<List<Liquidacion>>>

    @GET("liquidaciones/{id}")
    suspend fun getLiquidacion(@Path("id") id: String): Response<ApiResponse<Liquidacion>>

    @PUT("liquidaciones/{id}/pagar")
    suspend fun pagarLiquidacion(@Path("id") id: String, @Body request: PagarLiquidacionRequest): Response<ApiResponse<Liquidacion>>

    @PUT("liquidaciones/{id}/pendiente")
    suspend fun deshacerPagoLiquidacion(@Path("id") id: String): Response<ApiResponse<Liquidacion>>

    @DELETE("liquidaciones/{id}")
    suspend fun anularLiquidacion(@Path("id") id: String): Response<ApiResponse<Any>>

    // ========================================
    // DESCUENTOS AL GANADERO (adelantos, préstamos, leche rechazada)
    // ========================================

    @GET("descuentos")
    suspend fun getDescuentos(
        @Query("ganaderoId") ganaderoId: String? = null,
        @Query("semana") semana: Int? = null,
        @Query("anio") anio: Int? = null
    ): Response<ApiResponse<List<Descuento>>>

    @POST("descuentos")
    suspend fun crearDescuento(@Body request: DescuentoRequest): Response<ApiResponse<Descuento>>

    @DELETE("descuentos/{id}")
    suspend fun eliminarDescuento(@Path("id") id: String): Response<ApiResponse<Any>>

    // ========================================
    // MERMA DEL QUESO (se calcula sola al cierre de cada semana)
    // ========================================

    @GET("merma")
    suspend fun getMerma(@Query("fecha") fecha: String = Formato.hoyIso()): Response<ApiResponse<MermaResumen>>

    /** Único dato del cierre: kg de queso que pasan a la semana siguiente (0 si se vendió todo). */
    @PUT("merma/{anio}/{semana}")
    suspend fun confirmarCierre(
        @Path("anio") anio: Int,
        @Path("semana") semana: Int,
        @Body request: CierreRequest
    ): Response<ApiResponse<MermaResumen>>
}
