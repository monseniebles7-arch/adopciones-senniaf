# Sistema de Gestión de Adopciones — SENNIAF

**Versión hasta el Sprint 9** — HU-07.3 Resolución judicial obligatoria,
HU-07.4 Historial jurídico y HU-08.1 Búsqueda de familias por edad (5 SP).

Backend en Java (Spring Boot) con autenticación JWT y permisos separados
para Administrador del sistema, Equipo técnico y Juez.

## Sprint 9

| Historia | Quién | Qué hace |
|---|---|---|
| **HU-07.3** Resolución judicial obligatoria | Juez | Para pasar a *Declarado adoptable* exige número, fecha (no futura), juzgado y documento de la resolución. El documento se guarda cifrado en el expediente (categoría *Resolución judicial*). Sin esos datos el cambio se rechaza y el error nombra lo que falta |
| **HU-07.4** Historial jurídico | Equipo técnico y Juez | Línea de tiempo en la ficha, separada del historial general: del ingreso (*En protección*) a la etapa actual, con la observación, quién y cuándo, y la resolución con su documento. Los cambios hechos antes del Sprint 9 se muestran marcados como anteriores a la resolución obligatoria |
| **HU-08.1** Búsqueda de familias por edad | Equipo técnico | En la ficha de un menor *Declarado adoptable*, botón *Buscar familias compatibles*: familias con dictamen *Idónea* cuya edad mínima y máxima incluyen la edad del menor, en el orden de la lista de espera. Las familias sin rango de edad se cuentan aparte |

| Método y ruta | Roles | Respuestas |
|---|---|---|
| `POST /api/juez/expedientes/{numero}/declaracion-adoptabilidad` (multipart: `observacion`, `resolucion_numero`, `resolucion_fecha`, `resolucion_juzgado`, `archivo`) | Juez | 200 · 400 falta un dato o el archivo no es válido · 403 · 404 · 409 cerrado o no está *En proceso judicial* |
| `PATCH /api/juez/expedientes/{numero}/estado-juridico` | Juez | Igual que antes, pero `DECLARADO_ADOPTABLE` ahora responde 400: usa la ruta anterior |
| `GET /api/expedientes/{numero}/historial-juridico` | Equipo técnico y Juez | 200 · 403 Administrador · 404 |
| `GET /api/tecnico/expedientes/{numero}/familias-compatibles` | Equipo técnico | 200 · 403 Juez y Administrador · 404 · 409 el menor no está *Declarado adoptable* o el expediente está cerrado |

Tablas nuevas (se crean solas al arrancar, sin borrar `data`):
`resoluciones_judiciales` y `cambios_etapa_juridica`.

## Responsabilidades de los roles

- **Administrador del sistema (TI / Seguridad):** crear y consultar cuentas de usuario y revisar la auditoría de casos y cuentas. No accede a fichas ni modifica casos.
- **Equipo técnico de adopciones:** registra y actualiza expedientes y familias, gestiona documentos y dictámenes técnicos, y realiza reaperturas justificadas.
- **Juez:** consulta los casos y actualiza la etapa jurídica.

El Administrador no hereda los permisos del Equipo técnico. La auditoría registra consultas, cambios y creación de cuentas, y muestra solo metadatos. Las alertas automáticas, permisos granulares y gestión de respaldos aún requieren desarrollo adicional.

## Tecnologías

- Java 17 + Spring Boot 3 (Web, Security, Data JPA, Validation)
- Base de datos H2 en memoria (para el MVP; se puede sustituir por
  PostgreSQL/MySQL en un sprint posterior)
- JWT (io.jsonwebtoken / jjwt)
- BCrypt para el hash de contraseñas
- JUnit 5 + Mockito + MockMvc para las pruebas
- GitHub Actions para integración continua (compilación + pruebas)

## Cómo abrir el proyecto en IntelliJ

1. `File > Open...` y seleccionar la carpeta `adopciones-senniaf` (la que
   contiene el `pom.xml`).
2. IntelliJ detecta el proyecto Maven automáticamente y descarga las
   dependencias (requiere conexión a internet la primera vez).
3. Ejecutar la clase `AdopcionesApplication` (botón ▶ o `Shift+F10`).
4. La aplicación queda disponible en `http://localhost:8080`.

## Usuarios de prueba

Al iniciar la aplicación, `DataInitializer` crea automáticamente estos
usuarios (solo si no existen todavía) para poder probar los tres roles:

| Usuario   | Contraseña   | Rol            |
|-----------|--------------|----------------|
| admin     | Admin123!    | ADMINISTRADOR  |
| tecnico1  | Tecnico123!  | TECNICO        |
| juez1     | Juez123!     | JUEZ           |

> Estos usuarios son solo para el MVP/pruebas de este sprint. En un
> sprint posterior se reemplazan por un módulo real de administración
> de usuarios.

## Endpoints

### Login (público)

```
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "Admin123!"
}
```

Respuesta:

```json
{
  "token": "eyJhbGciOi...",
  "username": "admin",
  "rol": "ADMINISTRADOR"
}
```

### Endpoints protegidos por rol (demostración)

Enviar el token recibido en el header `Authorization: Bearer <token>`.

- `GET /api/admin/ping` → solo rol `ADMINISTRADOR`
- `GET /api/tecnico/ping` → solo rol `TECNICO`
- `GET /api/juez/ping` → solo rol `JUEZ`
- `GET /api/admin/auditoria` → solo `ADMINISTRADOR`; filtros opcionales `usuario`, `tipo`, `entidad`, `desde` y `hasta`, con paginación.

Un usuario sin el rol correcto recibe `403 Forbidden`; sin token o con
un token inválido/expirado recibe `401 Unauthorized`.

## Ejecutar las pruebas

Desde IntelliJ: clic derecho sobre `src/test/java` → `Run 'All Tests'`.

Desde la terminal:

```bash
mvn clean test
```

Pruebas incluidas:

- `AuthServiceTest`: login válido, contraseña incorrecta, usuario
  inexistente, usuario deshabilitado.
- `JwtServiceTest`: generación, validación y expiración de tokens.
- `RoleAccessIntegrationTest`: prueba de extremo a extremo (login real +
  acceso/rechazo a rutas protegidas según el rol).
- `Sprint9ApiIntegrationTest` (12 pruebas) y `EstadoJuridicoServiceTest`
  (4 pruebas nuevas): resolución obligatoria, historial jurídico y
  búsqueda por edad. En total, 96 pruebas.

## Integración continua (GitHub Actions)

El workflow `.github/workflows/ci.yml` se ejecuta en cada `push` y
`pull request` hacia `main`: compila el proyecto y corre todas las
pruebas JUnit con `mvn clean verify`, publicando el reporte de pruebas
como artefacto del workflow.

## Notas de seguridad de este sprint

- Las contraseñas nunca se guardan en texto plano (hash con BCrypt).
- La sesión es *stateless*: se usa un token JWT con expiración
  (configurable en `application.properties`, `app.jwt.expiration-minutes`).
- El secreto de JWT (`app.jwt.secret`) está en `application.properties`
  solo para fines de este MVP académico; en un entorno real debe
  inyectarse como variable de entorno y no debe subirse al repositorio.
