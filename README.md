# MundialFutbol Backend

Backend API para la aplicación de predicciones del Mundial 2026. Construido con Spring Boot 4 y Java 21.

## Stack Tecnológico

- **Java 25** + **Spring Boot 4.0.5**
- **Spring Security** — Autenticación JWT stateless
- **Spring Data JPA** — Persistencia con PostgreSQL 16
- **Spring WebSocket** — STOMP sobre WebSocket con SockJS fallback
- **Flyway** — Migraciones de base de datos
- **jqwik 1.9.2** — Property-based testing
- **jjwt 0.12.6** — Generación y validación de tokens JWT
- **Lombok** — Reducción de boilerplate

## Requisitos Previos

- Java 25 (configurado en `build.gradle` toolchain)
- PostgreSQL 16 corriendo localmente o en Docker
- Variable de entorno `FOOTBALL_DATA_API_KEY` (o usar el valor por defecto `demo-key`)

## Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Clave secreta para firmar tokens JWT (mínimo 32 caracteres) | Valor de desarrollo (no usar en producción) |
| `FOOTBALL_DATA_API_KEY` | API key de football-data.org | `demo-key` |
| `CORS_ORIGINS` | Orígenes permitidos para CORS | `http://localhost:4200` |

## Configuración de Base de Datos

Configurar en `application-dev.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mundialfutbol
    username: tu_usuario
    password: tu_password
```

## Ejecución

```bash
./gradlew bootRun
```

La aplicación arranca en `http://localhost:8080`.

## Endpoints Principales

### Autenticación
- `POST /api/auth/login` — Login (público)
- `PUT /api/auth/password` — Cambiar contraseña (autenticado)

### Partidos
- `GET /api/matches` — Listar partidos (filtros: `stageId`, `status`)
- `GET /api/matches/{id}` — Detalle de partido

### Predicciones
- `POST /api/predictions` — Crear predicción
- `PUT /api/predictions/{id}` — Modificar predicción
- `GET /api/predictions` — Mis predicciones
- `GET /api/predictions/match/{matchId}` — Mi predicción para un partido

### Ranking
- `GET /api/ranking` — Ranking global

### Administración (requiere rol ADMIN)
- `POST /api/admin/users/upload` — Carga masiva CSV
- `POST /api/admin/users/{id}/reset-password` — Reset de contraseña
- `PUT /api/admin/matches/{id}/result` — Ajustar resultado
- `PUT /api/admin/matches/{id}/status` — Transicionar estado
- `POST /api/admin/matches/{id}/recalculate` — Forzar recálculo de puntajes
- `GET /api/admin/audit-log` — Log de auditoría
- `POST /api/admin/seed` — Siembra de datos desde football-data.org

### WebSocket
- Endpoint: `/ws` (STOMP + SockJS)
- Topics:
  - `/topic/matches/{matchId}` — Actualizaciones de marcador en vivo
  - `/topic/ranking` — Ranking actualizado tras cálculo de puntajes

## Arquitectura

```
src/main/java/org/danielesteban/worldcupbetbackend/
├── config/              # SecurityConfig, WebSocketConfig, JpaAuditingConfig
├── domain/
│   ├── entity/          # Entidades JPA (User, Match, Prediction, etc.)
│   ├── enums/           # MatchStatus, UserRole
│   └── support/         # Auditable base class
├── integration/         # football-data.org (Client, StatusMapper, RateLimiter, DataSeeder, SyncScheduler)
├── persistence/
│   └── repository/      # Spring Data repositories
├── service/             # Lógica de negocio (Auth, Match, Prediction, Scoring, Ranking, Admin)
│   ├── dto/             # JwtClaims, ScoreBreakdown, CsvUploadResult, ExternalMatchDto, ExternalTeamDto
│   ├── event/           # MatchFinishedEvent, MatchAdjustedEvent
│   └── exception/       # Excepciones de dominio
├── web/
│   ├── controller/      # REST Controllers (Auth, Match, Prediction, Ranking, Admin)
│   ├── dto/             # Request/Response DTOs
│   ├── exception/       # GlobalExceptionHandler
│   └── security/        # JwtAuthenticationFilter, JwtAuthenticationToken
└── websocket/
    └── dto/             # MatchUpdateMessage, RankingUpdateMessage, RankingEntry
```

## Consideraciones Importantes

### Seguridad
- Los tokens JWT expiran en 24 horas (configurable via `app.jwt.expiration-ms`)
- Las contraseñas se almacenan hasheadas con BCrypt
- El endpoint de login no revela si un email existe o no (mensaje genérico)
- CSRF está deshabilitado (autenticación stateless)
- Los endpoints `/api/admin/**` requieren rol ADMIN

### Sincronización con football-data.org
- El `SyncScheduler` consulta la API cada 60 segundos
- Solo consulta cuando hay partidos LIVE o SCHEDULED próximos a iniciar (5 min)
- Rate limit: máximo 10 peticiones por minuto (tier gratuito)
- Reintentos con backoff exponencial (1s, 2s, 4s) para errores 5xx
- Los errores de la API no afectan datos existentes (log + retry next cycle)

### Scoring (Puntuación)
- Marcador exacto: **+4 puntos**
- Acertó ganador o empate: **+2 puntos**
- Penaltis exactos (solo eliminatorias): **+3 puntos bonus**
- Acertó ganador de penaltis: **+1 punto bonus**
- El recálculo se dispara automáticamente al ajustar un resultado

### WebSocket
- Heartbeats cada 10 segundos para detectar conexiones muertas
- La publicación es fire-and-forget: errores no afectan operaciones de negocio
- Sin autenticación JWT en WebSocket (datos públicos dentro de la organización)
- SockJS como fallback para navegadores sin soporte WebSocket nativo

### Máquina de Estados de Partidos
```
SCHEDULED → LIVE → FINISHED → ADJUSTED
SCHEDULED → POSTPONED → SCHEDULED
SCHEDULED → CANCELLED
LIVE → SUSPENDED → LIVE / FINISHED
```
- Las predicciones se bloquean al pasar a LIVE
- El scoring se dispara al pasar a FINISHED
- El recálculo se dispara al pasar a ADJUSTED

### Siembra de Datos (Data Seeding)
- Configurable via `football-data.seed.on-startup: true/false`
- Endpoint manual: `POST /api/admin/seed`
- Idempotente: puede ejecutarse múltiples veces sin duplicados
- Orden: equipos primero, luego partidos

### Testing
- **Property-based testing** con jqwik para propiedades universales de correctitud
- **Unit tests** con JUnit 5 + Mockito para casos específicos
- **@WebMvcTest** para tests de controladores con MockMvc
- Ejecutar tests: `./gradlew test`

## Ejecución con Docker

```bash
# Levantar PostgreSQL
docker run -d --name mundialfutbol-db \
  -e POSTGRES_DB=mundialfutbol \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin123 \
  -p 5432:5432 postgres:16

# Ejecutar la aplicación
JWT_SECRET=mi-clave-secreta-de-al-menos-32-caracteres \
FOOTBALL_DATA_API_KEY=tu-api-key \
./gradlew bootRun
```
