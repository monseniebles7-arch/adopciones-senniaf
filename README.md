# Sistema de Gestión de Adopciones — SENNIAF

**Sprint 1 — Acceso seguro y control de roles (HU-01, 5 SP)**

Backend en Java (Spring Boot) con autenticación JWT y permisos separados
para Administrador del sistema, Equipo técnico y Juez.

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
