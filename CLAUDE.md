# QuesoData — App Android

"Mi negocio, en orden". App Android (Kotlin, XML + ViewBinding, Navigation Component) para gestionar
una quesería: leche de ganaderos, producción, inventario, ventas, clientes y pagos.

## Arquitectura

- **Backend** (Node/Express + MongoDB Atlas) en `C:\Users\Moises\queseria-backend`
  (GitHub `moisarteaga18-ux/queseria-backend`, desplegado en `https://queseria-backend.onrender.com`).
  Todo se guarda en MongoDB: usuarios, leche, producción, precios, ventas, productos, inventario,
  clientes, ganaderos y pagos. Todas las rutas salvo `/api/auth` exigen JWT y filtran por `usuarioId`.
- **App** (GitHub `moisarteaga18-ux/queseria-app`, público): Retrofit + JWT en `api/RetrofitClient.kt`, contrato en `api/QueseriaApi.kt`, modelos en
  `models/`. No hay base de datos local (solo la sesión en SharedPreferences).
- La URL del backend sale de `BuildConfig.API_URL`. Por defecto es Render; para probar contra un
  backend local en el emulador: `./gradlew assembleDebug -PapiUrl=http://10.0.2.2:3000/api/`.
- Pantallas en `ui/<módulo>/`. Inicio es el menú principal (no hay menú inferior). Las llamadas a la API se hacen con `lifecycleScope` y el helper
  `api/ApiCall.kt` (maneja errores de red y sesión expirada → login).
- Convenciones: código, comentarios y textos de UI en español.

## Compilar

```
./gradlew assembleDebug
```

Backend local: `cd C:\Users\Moises\queseria-backend && node src/index.js` (usa `.env`).

## Plan (requerimientos de octubre 2026)

### Parte 1 — Todo en la nube
1. [x] Backend: modelos y rutas `productos`, `inventario` (movimientos), `clientes`, `ganaderos`.
       Ventas y producción aceptan `productoId` y mueven el stock en el servidor.
       Ventas guardan `clienteId` (ya no se pierden al renombrar un cliente).
2. [x] Backend: `resumen/hoy?fecha=` (número de ventas, costo de leche y ganancia estimada) y
       `resumen/semanas` (serie de 8 semanas para los gráficos).
3. [x] App: quitar Room; productos, inventario y clientes por API. Corregir modelos
       `ResumenHoy`/`Ganancias` y `unidad` ("libras", no "lb").
4. [x] Probado con backend local (MongoDB en memoria) + app en emulador: login real, CRUD,
       stock, pagos. Script de prueba de la API: 37/37 OK.
5. [x] Push del backend (commit e6b7963, aprobado por el usuario). Verificado contra Render/Atlas:
       pruebas de la API 37/37 y app en emulador (login real, leche guardada y leída de MongoDB).
       Los datos de prueba se borraron; queda el usuario `prueba_quesodata_render` (no hay ruta
       para borrar usuarios).

### Parte 2 — Pago semanal al ganadero
6. [x] Backend: `Ganadero` (nombre, teléfono), `Leche.ganaderoId`, `PrecioLeche` (precio del litro
       por semana), `Pago` (copia de litros/precio/total + fecha de pago). Rutas `pagos`.
7. [x] App: Ganaderos (`ui/ganaderos/`) y Pagos (semana, por ganadero: litros, precio, total,
       marcar/deshacer pago, historial). La leche se registra eligiendo ganadero (obligatorio).

### Parte 3 — Diseño QuesoData
8. [x] Nombre "QuesoData", ícono adaptativo vectorial (vaca + queso fresco), SplashActivity y
       login/registro con el logo (`drawable-nodpi/logo_quesodata.jpg`).
9. [x] Tema (`values/themes.xml`, `colors.xml`): verdes del logo, crema, blanco; Poppins
       (`res/font`, OFL); tarjetas `Queseria.Tarjeta` con sombra verde; pestañas tipo segmento;
       diálogos redondeados; filas con ícono (`Fila.icono`) y etiquetas tipo píldora.
10. [x] Gráficos en Reportes con `ui/common/Grafico.kt` (barras/línea dibujadas en Canvas).

## Plan (requerimientos 4 y 5) — orden: nube → semanas → liquidación PDF → diseño

### Parte 4 — Semanas de viernes a jueves (el jueves es el día de corte)
11. [x] Backend `utils/semana.js`: semana = viernes 00:00 a jueves 23:59:59 (UTC). Número de semana =
        semana ISO del jueves de corte (así `semana`/`anio` siguen siendo una clave única).
12. [x] Backend: corte aplicado en `resumen/ganancias` (`?fecha=`), `resumen/semanas`, `resumen/hoy`,
        `rendimiento/semanal` (agrupa en JS), `precios` (el servidor calcula las fechas) y precio de
        leche. Prueba local `prueba_semanas`: 17/17 (jueves = corte, viernes = semana nueva, año nuevo).
13. [x] App `util/Semana.kt` (+ `SemanaTest`) usado en Precios, Liquidaciones, Ganancias y Reportes.
        Probado en emulador (Precios y Ganancias muestran viernes a jueves).

### Parte 5 — Liquidación semanal al ganadero (PDF + WhatsApp)
14. [x] Backend: `Liquidacion` (reemplaza `Pago`, colección `liquidaciones`), `Descuento`, `Contador`
        (número consecutivo por usuario). Rutas `/api/liquidaciones` (semana, previa, resumen,
        generar, generar-semana, pagar, pendiente, anular, precio) y `/api/descuentos`.
        Al consultar una semana ya cerrada (pasó el jueves) se generan las liquidaciones que falten.
        Prueba local `prueba_liquidaciones`: 27/27.
15. [x] App `ui/liquidaciones/`: Liquidaciones (semana + historial), detalle (litros por día,
        descuentos, pagar con fecha/forma, anular, deshacer), PDF `LiquidacionPdf` (carta, logo,
        número, tabla, totales, estado), `CompartirPdf` (WhatsApp al número del ganadero con
        `jid`, o hoja de compartir), historial por ganadero (desde Ganaderos). Compila.
16. [x] Probado en emulador: semana cerrada genera N° 0001/0002 solas, detalle, descuentos, PDF en
        una página (borrador/pendiente y comprobante pagado), pagar por Nequi, compartir (sin WhatsApp
        instalado cae en la hoja de compartir), historial por ganadero. Push del backend (8b1c0ee) y
        pruebas contra Render 74/74; datos de prueba borrados.

### Parte 6 — Merma automática del queso (después de la liquidación en PDF)
17. [x] Backend `utils/merma.js` + `CierreSemana` (colección `cierres_semana`) + `/api/merma`:
        merma = inicial + producido − vendido (libras → kg) − kgFinal (0 por defecto). Se recalcula
        en cadena al consultar (ventas tardías incluidas); solo se guarda tal cual el kgFinal que
        confirma el usuario. % = merma / producido; promedio ponderado; stock estimado de la semana.
18. [x] App `ui/merma/CierreSemanal.kt`: en Inicio, pasado el jueves y solo si parece que sobró
        queso (> 1 kg o > 3 % de lo producido), un único dato: kg que pasan a la semana siguiente.
        Stock estimado en Inicio (tarjeta Queso) e Inventario.
19. [x] Reportes: gráfico de % de merma (8 semanas) y detalle kg/% de las últimas 4.
        Ganancias: "Pérdida por merma" (kg × precio por kg) por semana.
20. [x] Sin merma manual: se quitó "Ajuste" del inventario (el backend también lo rechaza).
        Pruebas: `prueba_merma` 17/17; total 92/92 local y contra Render (push 612b482).

### Seguridad del backend (commits 7a765e4 y 535a233)
- [x] `JWT_SECRET` solo por variable de entorno (Render → Environment); sin valor por defecto en el
      código y el servidor no arranca sin él (`src/config.js`). Antes Render firmaba con el valor fijo
      del repo público: cualquiera podía fabricar sesiones. Al rotarlo se cerraron todas las sesiones.
- [x] HS256 fijo, sesiones de 180 días, el usuario del token debe existir y estar activo.
- [x] Login: 10 fallos por usuario y 30 por IP cada 15 min → 429 (`utils/limite.js`, en memoria);
      tipos validados (sin inyección NoSQL); contraseña mínima de 6 caracteres.
- [x] `.env` fuera del repo + `.gitignore`. Sigue en el historial de git (decisión del usuario: no
      reescribirlo y cambiar la contraseña de MongoDB Atlas).
- [x] Contraseña del usuario de la base cambiada en Atlas y `MONGODB_URI` actualizada en Render
      (24 oct 2026, por el usuario). Verificado contra Render: pruebas de la API 118/118
      (api, semanas, liquidaciones, merma, semanas_liq, seguridad); datos de prueba borrados.

## Plan (requerimiento 7, octubre 2026) — interfaz más fácil de operar

### Parte 7 — Menú de inicio y navegación
21. [x] Inicio = menú principal (`InicioFragment` + `item_menu.xml`): resumen del día (leche, queso,
        ventas, lo que se debe a los ganaderos) y tarjetas grandes en 3 secciones: Todos los días
        (Registrar leche, Producción del día, Nueva venta, Inventario), Cada semana (Liquidación a
        ganaderos, Precios, Reportes) y Configuración (Ganaderos, Clientes, Productos).
22. [x] Sin menú inferior. Barra superior: flecha atrás + casita "Ir al inicio" en todas las pantallas
        (menú ⋮ solo con Cerrar sesión). Leche y Producción son dos pantallas (`nav_leche`,
        `nav_produccion`, mismo `LecheFragment` con argumento `modo`).
23. [x] Formularios: fecha de hoy, valores comunes ya puestos (último ganadero, último producto, litros
        de producción = leche de hoy sin usar), cursor y teclado numérico en el primer dato, avisos
        claros si falta algo y mensaje "Listo: …" al guardar. Botón flotante con texto ("+ Nueva venta").
        Pantallas vacías que dicen qué tocar.

### Parte 8 — Liquidación al ganadero (flujo simple)
24. [x] Causa de "no puedo generar la liquidación": la función existía, pero (a) sin precio del litro
        el botón "Generar" quedaba desactivado sin decir qué hacer, (b) la pantalla abría la semana
        anterior aunque la leche fuera de la actual, y (c) la leche antigua sin `ganaderoId`
        (solo `proveedor`) no entraba en ninguna liquidación.
25. [x] Flujo: menú → semanas (`LiquidacionesFragment`, con lo que se debe en cada una; pestaña
        Historial) → ganaderos de la semana (`SemanaLiquidacionFragment`) → detalle → "Definir precio
        del litro" (si falta) → "Generar liquidación" → ofrece WhatsApp/Compartir → "Marcar como pagado".
26. [x] Backend: `GET /api/liquidaciones/semanas?n=` (resumen por semana), `PUT /api/leche/:id`
        (corregir/asignar ganadero) y asignación automática de la leche antigua cuyo `proveedor`
        coincide con un único ganadero. En Leche recibida, los registros "Sin ganadero" se tocan para
        asignarlo. Pruebas: `prueba_semanas_liq` 13/13 + anteriores 92/92 (local).
27. [x] Probado de principio a fin en el emulador (backend local): precio → generar N° 0001 → PDF →
        compartir → pagar por Nequi; la semana cerrada generó sola la N° 0002.

## Plan (requerimientos de 26 oct 2026) — limpieza, calendario por semanas, edición

### Parte 9 — Limpieza de la base
28. [x] `pagos`: ningún modelo la usa (la reemplazó `liquidaciones`) → se elimina si está vacía.
        `descuentos` SÍ se usa (adelantos, préstamos, leche rechazada en la liquidación): se conserva.
29. [x] Costo del litro en un solo lugar: `precioleches.precioLitro`. Se quitó `Precio.costoLitroLeche`;
        `/api/precios` devuelve por semana `precioPorKg` (queso) y `precioLitro` (leche) y se edita con
        `PUT /api/precios/semana` (`utils/precios.js`). Causa del 0 en Precios: el litro se definía en
        Liquidaciones (`precioleches`) y Precios mostraba `costoLitroLeche` de `precios` (vacío).
30. [x] `Leche.proveedor` eliminado (el nombre sale del ganadero: `nombreGanadero` en `GET /api/leche`);
        `ganaderoId` obligatorio. Se quitó la asignación automática por nombre de `liquidaciones.js`.
31. [ ] Migración `scripts/migrar-limpieza-2026-10.js` (simulación por defecto; `--aplicar` guarda
        respaldo JSON en `scripts/`, fuera de git): borra `pagos` vacía, pasa `costoLitroLeche` a
        `precioleches` (si ya hay precio ahí, gana ese) y lo quita, asigna la leche con solo `proveedor`
        al ganadero del mismo nombre (o crea el ganadero; si hay dos con el mismo nombre no la toca) y
        quita `proveedor`. Aplicada en Atlas el 26 oct 2026 (aprobada por el usuario): `pagos` eliminada,
        4 precios del litro creados (de usuarios de prueba), `costoLitroLeche` quitado de 10 precios,
        `proveedor` quitado de 4 registros (ya tenían ganadero). Semana 44 de "moises el grande": 1.600
        (confirmado por el usuario). Respaldo en `scripts/respaldo-2026-10-26T16-27-41-618Z.json`.

### Parte 10 — Calendario por semanas, edición y producción con la leche del día
32. [x] Registrar leche: `ui/home/SelectorSemana.kt` — "Semana 44: vie 23 oct – jue 29 oct" con flechas
        (no pasa de la semana actual) y los 7 días (vie…jue) con los litros ya registrados
        (`GET /api/leche/dias?desde=&hasta=`); por defecto hoy. Debajo, lo que ya hay ese día y del ganadero.
33. [x] Editar sin borrar (tocar el registro): Precios (por semana), Leche, Producción (`PUT /api/produccion/:id`)
        y Ventas (`PUT /api/ventas/:id`). Inventario: `reemplazarMovimiento` deshace el movimiento anterior y
        aplica el nuevo (si no alcanza el stock, deja todo como estaba). Liquidaciones pendientes,
        rendimiento, merma y ganancias se calculan al consultarlos.
34. [x] Semana ya pagada: leche (crear/corregir/borrar litros, fecha o ganadero) y precio del litro
        responden 409 `requiereConfirmacion`; la app muestra el aviso (`guardarConConfirmacion`) y reenvía
        con `confirmar: true`. Confirmado, la liquidación pagada se recalcula, sigue pagada y guarda
        `totalPagado`; el detalle muestra la diferencia. Producción y ventas no cambian lo pagado.
35. [x] Producción: litros usados = leche recibida ese día (todos los ganaderos) menos la ya usada en otra
        producción del mismo día; se recalcula al cambiar la fecha y se puede cambiar. Si se registran más
        litros de los recibidos, avisa y pide confirmación. Rendimiento (lb por lata) con esos litros.
36. [x] Pruebas de la API (local): `prueba_edicion` 41/41 + anteriores actualizadas 126/126. App compila
        y pasan las pruebas unitarias; APK en el Escritorio (`QuesoData.apk`, apunta a Render).
37. [x] Push del backend (984fe82, aprobado por el usuario). Contra Render: 154/154 (edicion, api, semanas,
        liquidaciones, merma, semanas_liq); datos de prueba borrados (quedan usuarios `r26_*` y sus precios).
38. [x] Render tras la migración: edicion 41/41 y liquidaciones 34/34. `.env` local corregido
        (`MONGODB_URI=` delante; antes era solo la URI y dotenv no la leía).
39. [x] Probar la app en el emulador (ver 48).
40. [x] Usuarios de prueba borrados (19 `prueba_*`, 8 `r26_*` y `moises_test`, 59 documentos), con respaldo en
        `Respaldos QuesoDataorrado-usuarios-prueba-2026-10-26.json`. Quedan "moises el grande" y "moises arteaga".

### Parte 11 — Libra de 500 g, tipos numéricos, respaldos y seguridad (26 oct 2026)
41. [x] Libra colombiana: 1 kg = 2 lb. Una constante en el backend (`utils/unidades.js`: `LIBRAS_POR_KG`,
        `aKg`, `precioPorLibra`, `librasPorLata`) y otra en la app (`Formato.LIBRAS_POR_KG`). Rendimiento
        guardado = (kg × 2) ÷ (litros ÷ 20) lb por lata (antes litros ÷ kg). En Atlas no hay ventas en libras.
42. [x] Ventas: sí se guardan (1 venta de "moises arteaga" del 23 oct con su movimiento de inventario).
43. [x] Tipos: los decimales usan `decimal()` (Schema.Types.Double + getter a número) en `models/index.js`;
        antes un mismo campo quedaba int o double según el valor ("tipo mixto" en Atlas).
        `scripts/migrar-tipos-2026-10.js` corrige los registros existentes. Probado en local (22/22 double).
44. [x] Seguridad: `node_modules` estaba en el repositorio (sale); mongoose 8.24.7, express 4.22.1, qs 6.16.1
        (override): `npm audit` 0. La URI del historial tiene la contraseña vieja (alerta para marcar como revocada).
45. [x] Respaldo: `scripts/respaldar.js` (+ `respaldo-semanal.cmd` para el Programador de tareas) →
        `%USERPROFILE%Respaldos QuesoDataqueseria-AAAA-MM-DD_HHMM.json` (Extended JSON, fuera del repo).
46. [x] Push del backend (ea5ca1e, aprobado) tras cambiar la contraseña de Atlas (usuario: Atlas, Render y .env).
        Instalación limpia desde GitHub: npm audit 0 (las 5 de Render eran del lock anterior). Render 167/167;
        las ventas de prueba quedaron en Atlas como double. Usuarios `r27_*` borrados. Respaldo real:
        `Respaldos QuesoData\queseria-2026-10-26_1149.json`.
47. [x] `migrar-tipos-2026-10.js --aplicar` en Atlas (26 oct 2026, aprobado): 24 actualizaciones de tipo
        (int → double) y 3 rendimientos recalculados; respaldo previo en
        `Respaldos QuesoData\antes-de-migrar-tipos-2026-10-26.json`. La simulación posterior ya no encuentra nada.
48. [x] Probada toda la app en el emulador (26 oct 2026, tras reiniciar Windows; backend local con Mongo en
        memoria): registro/login, ganaderos, clientes, productos, leche (semanas, editar, borrar), producción,
        inventario, ventas (editar/borrar, sin stock), precios, liquidaciones (generar, descuento, PDF, compartir,
        pagar, deshacer, anular, semana pagada corregida), reportes, ganancias, rendimiento y cierre de merma.
        Corregido: Registrar leche se cerraba al abrir (`SelectorSemana` avisaba desde su `init`, antes de
        asignarse) y Ganancias mostraba "▲ 0 %" cuando la semana anterior no tuvo ganancia.
49. [x] Ajustes tras la prueba: "Nueva venta" propone el producto de la última venta; "pagada (Nequi)" con
        mayúscula (`FormaPago.enFrase`); merma sin precio del queso dice que falta el precio (no "−$ 0").
50. [x] Backend: anular guarda la liquidación en `liquidaciones_anuladas` y la semana cerrada ya no la
        regenera sola (se genera a mano). `PUT`/`DELETE /api/inventario/movimientos/:id` solo para movimientos
        manuales (sin `referencia`); el stock cambia en la diferencia y nunca queda negativo. App: tocar un
        movimiento manual lo corrige, mantenerlo lo elimina. Pruebas: `prueba_anular_mov` 19/19 + anteriores
        136/136 (local); verificado en el emulador.
51. [x] Push del backend (061a32a, aprobado por el usuario). Contra Render: 155/155 (api, liquidaciones,
        edicion, merma, seguridad, anular_mov). Usuarios de prueba borrados de Atlas (8 `prueba_*`, 35 documentos;
        aprobado), con respaldo en `Respaldos QuesoData\borrado-usuarios-prueba-2026-10-26b.json`.
        Quedan "moises el grande" y "moises arteaga".

### Parte 12 — Mi cuenta: perfil y contraseña (26 oct 2026)
52. [x] Backend: `Usuario` con `propietario`, `telefono`, `direccion`, `nit` y `passwordCambiadoEn`.
        `PUT /api/auth/perfil` (solo los campos enviados; "" borra; nombre del negocio obligatorio; no cambia
        username/password/activo). `PUT /api/auth/contrasena {actual, nueva}` (límite de 10 fallos como el login;
        devuelve token nuevo). `verificarToken` rechaza tokens emitidos antes de `passwordCambiadoEn`.
53. [x] App: sección "Mi cuenta" en Inicio → `ui/cuenta/PerfilFragment` (`nav_perfil`) y `ContrasenaFragment`
        (`nav_contrasena`). Guardar el perfil actualiza el nombre del negocio en la sesión (Inicio y PDF).
        Pruebas: `prueba_cuenta` 23/23 + anteriores 155/155 (local). App compila.
54. [x] Push del backend (46f5c62, aprobado). Contra Render: 178/178 (api, liquidaciones, edicion, merma,
        seguridad, anular_mov, cuenta). APK nuevo en el Escritorio. Usuarios `prueba_*` borrados de Atlas
        (8 usuarios, 75 documentos), respaldo en `Respaldos QuesoData\borrado-usuarios-prueba-2026-10-26c.json`.
55. [ ] Probar "Mi cuenta" en el emulador (se cerró por falta de memoria; abrirlo solo si el usuario lo pide).
56. [x] Recuperar contraseña por correo. Backend: `utils/correo.js` con la API HTTPS de Brevo (Render gratis
        bloquea SMTP): `BREVO_API_KEY`, `CORREO_DE` (remitente verificado en Brevo), `CORREO_NOMBRE` opcional;
        `CORREO_ARCHIVO` y `BREVO_URL` solo en pruebas locales. Sin dependencias nuevas (usa `fetch`).
        `POST /api/auth/recuperar {username}` (código de 6 dígitos, 15 min; se guarda su HMAC; misma respuesta
        exista o no el usuario; 3 códigos por usuario y 10 por IP cada 15 min; 503 si falta configurar SMTP) y
        `POST /api/auth/restablecer {username, codigo, nueva}` (5 intentos por código, 20 por IP; cierra las
        demás sesiones y devuelve token). `engines.node >= 20`. App: "¿Olvidaste tu contraseña?" en el login →
        `ui/auth/RecuperarActivity`. Pruebas: `prueba_recuperar` 21/21 + `cuenta` 23 + `anular_mov` 19 +
        anteriores 136 (local). App compila.
57. [x] Push del backend (8e74000, aprobado). Contra Render: 178/178; `/auth/recuperar` responde 503 hasta
        configurar Brevo. Usuarios `prueba_*` borrados (respaldo `borrado-usuarios-prueba-2026-10-26d.json`).
        APK nuevo en el Escritorio. Los dos usuarios reales tienen correo de Gmail en el perfil.
58. [ ] El usuario crea la cuenta de Brevo y pone `BREVO_API_KEY` y `CORREO_DE` en Render; luego probar
        el envío real de un código.

## Parte 13 — Diseño visual de Inicio (29 oct 2026, solo cambios visuales)
59. [x] Inicio con el diseño de `imagenes_quesodata/` (LEEME.txt trae los colores): cabecera con
        `img_cabecera_campo` y logo redondo, resumen del día, "Hoy" (Registrar leche y Producción, que el
        diseño no traía y no se podían quitar), 4 tarjetas grandes con ilustración, Reportes, "Más opciones"
        y barra inferior. Inicio oculta la barra superior; Cerrar sesión está en el menú ≡ de la cabecera.
        Campana y Configuración: solo diseño (sin pantalla). Colores `qd_*` en `colors.xml`.
        Corregido en el tema: `fontFamily` (AppCompat) anulaba las negritas de Poppins en toda la app.
60. [x] Modo claro/oscuro: Inicio → Configuración (claro, oscuro o igual que el teléfono). `util/Tema.kt`
        (SharedPreferences `queseria_ajustes`, por defecto claro) aplicado en `QuesoDataApp`. Tema DayNight;
        colores oscuros en `values-night/colors.xml` con los mismos nombres; fondos blancos → `superficie`.
        Las tarjetas con ilustración siguen pastel en modo oscuro (las imágenes se funden con ese color) y el
        PDF siempre se dibuja con la paleta clara.
61. [x] Ventas solo a clientes registrados: el cliente se elige de una lista (obligatorio), con
        "+ Agregar cliente" en el mismo formulario. La venta guarda `clienteId`. Solo en la app: el backend
        sigue aceptando ventas sin cliente (las antiguas "Cliente ocasional" se conservan).

## Parte 14 — Precio mayorista/minorista, Configuración, adelantos y vidrio (30 oct 2026)
62. [x] Backend: `Precio.precioMayoristaPorKg` (opcional; `precioPorKg` = minorista y ya no es obligatorio).
        `PUT /api/precios/semana` acepta `precioMayoristaPorKg`; las respuestas traen también
        `precioMayoristaPorLibra`. Prueba local `prueba_precios` 8/8. Sin push todavía.
63. [x] App: Precios muestra y edita minorista y mayorista (por kg, con su equivalente por libra). Ventas:
        "Por libras" pasa a llamarse "Minorista" (el valor guardado sigue siendo `libras`); el precio se pone
        solo con el de la semana de la venta según el tipo y la unidad (cambia al cambiar tipo, unidad o
        fecha); sin precio de la semana usa el del producto (solo minorista). El cliente fija el tipo.
64. [x] Configuración (tarjeta, menú ≡ y "Cuenta" de la barra inferior) = una ventana con Mi cuenta, Cambiar
        contraseña, Apariencia y Cerrar sesión. Se quitaron las tarjetas Mi cuenta y Cambiar contraseña.
65. [x] "Adelanto o descuento" en Hoy (Inicio): elige ganadero y la semana (flechas, hasta 8 adelante) en
        que se descuenta. El mismo formulario (semana en vez de fecha) se usa en Ganaderos, historial y detalle.
        Fecha guardada: hoy si cae en la semana; si no, su viernes (futura) o su jueves (pasada).
66. [x] Inicio con efecto vidrio: foto del campo fija detrás (desenfocada con `RenderEffect` en Android 12+)
        con velo `velo_fondo`; tarjetas semitransparentes (`vidrio`, `bg_vidrio`) sin sombra y con borde claro.
        Compila y pasan las pruebas unitarias; falta verlo en el emulador.
67. [x] Push del backend (5d0eb33, pedido por el usuario). Contra Render: `prueba_precios` 8/8. APK nuevo en el
        Escritorio. Quedan en Atlas 2 usuarios `prueba_precios_*` (borrarlos con respaldo si el usuario lo aprueba).

## Plan (2 nov 2026) — Ventas/Producción por semana y diseño de toda la app
Solo organización y diseño: sin cambiar fórmulas, cálculos, llamadas a la API, backend ni base de datos.
Guía: `1000167543.png` (Adelanto o descuento, Registrar producción) y `1000167551.png` (Inicio, Ventas).
Se conserva "QuesoData" y "Tu negocio, en orden".

### Parte 15 — Ventas y Producción por semana
68. [x] Selector de semana (vie–jue, flechas, por defecto la actual) arriba de Ventas (reemplaza Desde/Hasta)
        y Producción. Ventas: `getVentas(desde=vie, hasta=jue)`. Producción: `getProduccion(limit=1000)` filtrada
        por semana en la app (la ruta no tiene filtro de fechas).
69. [x] Resumen de la semana: Ventas ($, kg, lb, n.º, mayorista/minorista); Producción (litros, kg, lb por lata).
70. [x] Lista agrupada por día (vie → jue) con encabezado y subtotal; días sin registros en gris
        (`ui/common/ListaPorDias.kt`: `NavegadorSemana` + `ListaPorDias`; Ventas pide hasta jue 23:59:59Z).
71. [x] Corte probado (backend local, script `prueba_corte` 14/14 + emulador): ventas y producción del jueves 29 oct en
        la semana 44 y del viernes 30 en la 45; totales iguales a Reportes (`resumen/semanas`) y Ganancias.

### Parte 16 — Diseño de toda la app
72. [x] Ilustraciones recortadas de la guía → WebP (`drawable-nodpi/img_guia_*`, 15; íconos sin fondo). Avisar las borrosas.
73. [x] Base (`cabecera_ilustrada`, `formulario_completo`, estilos Etiqueta/Input/CampoFecha/Desplegable/Opcion/Aviso/
        BotonCancelar/BotonGuardar, `ic_campo_*`, `Aviso.kt`, `Calendario.kt`): cabecera verde ilustrada con borde ondulado, tarjetas crema, campos grandes redondeados,
        etiqueta con ícono de color, opciones como tarjetas, aviso verde, botones Cancelar/Guardar abajo.
74. [x] Formularios: los 14 de `mostrarFormulario` (leche, producción, ventas, precios, ganaderos, clientes, productos,
        inventario, descuentos, liquidaciones, cierre de merma…: íconos, opciones-tarjeta, avisos, semanas), Mi cuenta y
        Cambiar contraseña (cabecera propia, sin barra superior, Cancelar/Guardar) y login, registro y recuperar.
75. [x] Listas: franja ilustrada con borde ondulado (`fragment_lista`); barra superior verde con título centrado.
        Ventas: foto del queso (rueda = minorista, bloques = mayorista) y total en píldora verde (`Fila.imagen`).
        Barra inferior en toda la app (`barra_inferior.xml`, MainActivity): Inicio, Reportes, Ventas y Más (las otras
        pantallas + Configuración); activa en amarillo. Inicio: cabecera verde con la vaca, "QuesoData" y "Tu negocio,
        en orden", engranaje = Configuración; tarjetas crema con sombra (reemplaza el vidrio de la Parte 14) y paisaje al pie.
        Listas: la franja y la cabecera suben con la lista (AppBarLayout). Ilustraciones opacas (quesos, leche,
        cantina, precios) con desvanecido arriba/izquierda para fundirse con la cabecera verde.
76. [~] Emulador (2 nov 2026, backend local): login, Inicio, barra inferior, Más, cierre de merma (guardar), Ventas
        (semanas 44/45, nueva venta), Producción (editar y eliminar), Mi cuenta (guardar), Registrar leche y Adelanto
        (guardar). Sin abrir en el emulador: precios, ganaderos, clientes, productos, inventario, liquidaciones,
        contraseña, registro y recuperar (compilan con el mismo marco). APK en el Escritorio (Render).
77. [x] Ajustes de Inicio (2 nov 2026): sin el paisaje del final (se montaba sobre Productos/Configuración); cabecera
        con alto del contenido (RelativeLayout, foto con alignTop/alignBottom) y 20dp libres bajo la fecha antes del
        resumen; "QuesoData" (un texto, "Data" dorado por código) y "Tu negocio, en orden" en una línea con autoSize;
        tarjetas grandes en una columna si el ancho es < 360 dp. Probado en emulador a 320 dp, 411 dp y 720 dp.

## Decisiones
- Semanas de viernes a jueves (antes ISO lunes–domingo), calculadas en UTC; la app envía fechas
  `yyyy-MM-dd`. El número de semana es el de la semana ISO del jueves de corte.
- Los precios semanales guardados antes del cambio conservan sus fechas (lunes–domingo).
- La merma se calcula sobre el total de queso (no por producto). El stock por producto de
  Inventario no descuenta la merma; el stock real de la semana es el "estimado" de `/api/merma`.
- El valor de la merma se muestra como pérdida informativa y NO se resta otra vez de la ganancia:
  es queso que no se vendió (ya falta en los ingresos) y su leche ya está en el costo.
- Mientras la última semana cerrada espera su dato (kg que pasan), no cuenta en el promedio.
- Una liquidación pendiente se recalcula sola; al pagarla queda congelada como comprobante.
  Los PDF no se guardan: se generan en el teléfono con los datos guardados en MongoDB.
- El costo del litro (Ganancias, Reportes, liquidaciones, Precios) sale solo de `precioleches`; sin
  precio, el costo es 0.
- Una liquidación pagada no cambia sola. Si se corrige su semana (leche o precio del litro), la app
  pide confirmación; confirmado, se recalcula y conserva en `totalPagado` lo que se pagó (diferencia
  = lo que falta pagar o lo pagado de más). Los descuentos de una semana pagada siguen bloqueados.
- Cambiar solo la nota de un registro de leche no pide confirmación.
- Libra = 500 g (1 kg = 2 lb), no la libra inglesa (453,6 g).
- Los respaldos van en `%USERPROFILE%\Respaldos QuesoData` (o `RESPALDOS_DIR` del .env), nunca en el repositorio.
- Un ganadero con leche o pagos no se borra: se archiva (`activo: false`).
- Una liquidación anulada no se vuelve a generar sola al consultar la semana cerrada.
- Solo se corrigen/eliminan a mano los movimientos manuales; los de ventas y producción se corrigen desde allí.
- El usuario de inicio de sesión (username) no se cambia desde el perfil.
- Cambiar la contraseña cierra las sesiones de los otros teléfonos; el que la cambió recibe un token nuevo.
- Los datos que había en Room (solo pruebas) no se migraron.
- Dos precios del queso por semana: minorista (`precioPorKg`, el que usa la merma) y mayorista. La venta
  "libras" es la minorista. El precio de la venta se puede cambiar a mano.
- El `.env` local del backend apunta a un cluster de Atlas que ya no existe; Render usa otra
  `MONGODB_URI` en sus variables de entorno.
