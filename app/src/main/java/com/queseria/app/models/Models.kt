package com.queseria.app.models

import com.google.gson.annotations.SerializedName

// ========================================
// RESPUESTA GENÉRICA DE LA API
// Todas las respuestas del backend siguen este patrón:
// { "ok": true, "data": {...} }  o  { "ok": false, "error": "mensaje" }
// ========================================
data class ApiResponse<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null,
    // 409: el cambio afecta una liquidación ya pagada; hay que preguntar y reenviar con confirmar = true
    val requiereConfirmacion: Boolean? = null
)

// ========================================
// AUTENTICACIÓN
// ========================================
data class LoginRequest(
    val username: String,
    val password: String
)

data class RegistroRequest(
    val username: String,
    val password: String,
    val nombreNegocio: String,
    val email: String
)

data class AuthResponse(
    val token: String? = null,
    val usuario: Usuario? = null
)

// ========================================
// USUARIO
// ========================================
data class Usuario(
    @SerializedName("_id") val id: String? = null,
    val username: String,
    val nombreNegocio: String,
    val email: String? = null,
    val propietario: String? = null,
    val telefono: String? = null,
    val direccion: String? = null,
    val nit: String? = null,
    val activo: Boolean = true,
    val createdAt: String? = null
)

/** Cambios del perfil. Un campo vacío ("") se borra; el nombre del negocio es obligatorio. */
data class PerfilRequest(
    val nombreNegocio: String,
    val propietario: String,
    val telefono: String,
    val email: String,
    val direccion: String,
    val nit: String
)

data class CambiarContrasenaRequest(
    val actual: String,
    val nueva: String
)

/** Recuperar la contraseña: 1) pedir el código al correo, 2) restablecer con el código. */
data class RecuperarRequest(val username: String)

data class RecuperarResponse(
    val mensaje: String? = null,
    val minutos: Int = 15
)

data class RestablecerRequest(
    val username: String,
    val codigo: String,
    val nueva: String
)

// ========================================
// LECHE
// ========================================
data class Leche(
    @SerializedName("_id") val id: String? = null,
    val usuarioId: String? = null,
    val fecha: String,
    val litros: Double,
    val ganaderoId: String? = null,
    // Nombre actual del ganadero (lo agrega el backend)
    val nombreGanadero: String? = null,
    val observaciones: String? = null
)

data class LecheRequest(
    val litros: Double,
    val ganaderoId: String? = null,
    val observaciones: String? = null,
    val fecha: String? = null,
    // true = el usuario aceptó cambiar una semana ya pagada
    val confirmar: Boolean? = null
)

/** Un día en GET /api/leche/dias: litros recibidos (total y por ganadero) y usados en producción. */
data class DiaLeche(
    val fecha: String,
    val litros: Double = 0.0,
    val entregas: Int = 0,
    val litrosUsados: Double = 0.0,
    val porGanadero: List<LitrosGanadero> = emptyList()
)

data class LitrosGanadero(val ganaderoId: String, val litros: Double = 0.0)

// ========================================
// PRODUCCIÓN (cuando convierten leche en queso)
// ========================================
data class Produccion(
    @SerializedName("_id") val id: String? = null,
    val usuarioId: String? = null,
    val fecha: String,
    val litrosUsados: Double,
    val kgProducidos: Double,
    val rendimiento: Double? = null,
    val observaciones: String? = null,
    val productoId: String? = null
)

data class ProduccionRequest(
    val litrosUsados: Double,
    val kgProducidos: Double,
    val observaciones: String? = null,
    val fecha: String? = null,
    // Producto al que se suma el queso en el inventario (opcional)
    val productoId: String? = null
)

// ========================================
// PRECIOS DE UNA SEMANA (viernes a jueves)
// Queso por kg (minorista = precioPorKg, y mayorista) y litro de leche (el mismo de las
// liquidaciones). null = no definido.
// ========================================
data class Precio(
    @SerializedName("_id") val id: String? = null,
    val semana: Int,
    val anio: Int,
    val fechaInicio: String,
    val fechaFin: String,
    val precioPorKg: Double? = null,
    val precioPorLibra: Double? = null,
    val precioMayoristaPorKg: Double? = null,
    val precioMayoristaPorLibra: Double? = null,
    val precioLitro: Double? = null,
    val activo: Boolean = false
)

/** PUT /api/precios/semana: los valores en null no se cambian. */
data class PrecioSemanaRequest(
    val semana: Int,
    val anio: Int,
    val precioPorKg: Double? = null,
    val precioMayoristaPorKg: Double? = null,
    val precioLitro: Double? = null,
    val confirmar: Boolean? = null
)

// ========================================
// VENTA
// ========================================
data class Venta(
    @SerializedName("_id") val id: String? = null,
    val usuarioId: String? = null,
    val fecha: String,
    val tipoVenta: String,
    val cliente: String? = null,
    val clienteId: String? = null,
    val productoId: String? = null,
    val cantidad: Double,
    // "kg" o "libras"
    val unidad: String,
    val precioUnitario: Double,
    val totalVenta: Double? = null
)

data class VentaRequest(
    val tipoVenta: String,
    val cliente: String? = null,
    val clienteId: String? = null,
    // Producto que se descuenta del inventario (opcional)
    val productoId: String? = null,
    val cantidad: Double,
    val unidad: String,
    val precioUnitario: Double,
    val fecha: String? = null
)

// ========================================
// RESUMEN DEL DÍA
// Endpoint: GET /api/resumen/hoy?fecha=yyyy-MM-dd
// ========================================
data class ResumenHoy(
    val litrosHoy: Double = 0.0,
    val kgProducidosHoy: Double = 0.0,
    val totalVentasHoy: Double = 0.0,
    val numeroVentasHoy: Int = 0,
    val costoLecheHoy: Double = 0.0,
    val gananciaEstimadaHoy: Double = 0.0
)

// ========================================
// GANANCIAS (semana actual vs anterior)
// Endpoint: GET /api/resumen/ganancias
// ========================================
data class Ganancias(
    val semanaActual: GananciaSemana,
    val semanaAnterior: GananciaSemana,
    val variacion: Double = 0.0,
    val porcentaje: Double = 0.0
)

data class GananciaSemana(
    val semana: Int = 0,
    val anio: Int = 0,
    val rango: RangoFechas? = null,
    val ingresos: Double = 0.0,
    val litrosComprados: Double = 0.0,
    val costoLitro: Double = 0.0,
    val costoLeche: Double = 0.0,
    val ganancia: Double = 0.0,
    val precioVentaKg: Double = 0.0,
    // Merma del queso de la semana (estimada si la semana sigue abierta), valorada a precio de venta
    val mermaKg: Double = 0.0,
    val mermaPct: Double = 0.0,
    val mermaEstimada: Boolean = false,
    val valorMerma: Double = 0.0
)

data class RangoFechas(
    val inicio: String? = null,
    val fin: String? = null,
    // "23 – 29 oct 2026" (viernes a jueves)
    val texto: String? = null
)

// ========================================
// SERIE SEMANAL (gráficos de Reportes)
// Endpoint: GET /api/resumen/semanas?n=8
// ========================================
data class ResumenSemana(
    val semana: Int,
    val anio: Int,
    val fechaInicio: String,
    val fechaFin: String,
    val ingresos: Double = 0.0,
    val litrosLeche: Double = 0.0,
    val costoLeche: Double = 0.0,
    val ganancia: Double = 0.0,
    val kgProducidos: Double = 0.0,
    val librasPorLata: Double = 0.0,
    val mermaKg: Double = 0.0,
    val mermaPct: Double = 0.0,
    val mermaEstimada: Boolean = false
)
