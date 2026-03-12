# BunnyCure – README técnico

Documento técnico actualizado al **12 de marzo de 2026** para describir el estado real del proyecto en código. No se incluyen credenciales, passwords ni secretos de ningún ambiente.

---

## 1. Resumen ejecutivo técnico

BunnyCure es una aplicación MVC con Spring Boot orientada a:

- capturar solicitudes públicas de reserva,
- convertirlas en citas administradas internamente,
- mantener ficha de clientas,
- notificar por email y WhatsApp,
- procesar eventos entrantes del webhook de WhatsApp,
- y operar con perfiles `local` y `heroku`.

El sistema está estructurado en capas clásicas `controller -> service -> repository -> entity`, usa Flyway para evolución del esquema y aplica reglas de seguridad adicionales para cuentas administrativas: cambio obligatorio de password legacy y recuperación por email con token temporal.

---

## 2. Stack y runtime verificados

### Dependencias principales (`pom.xml`)

| Componente | Versión |
|---|---:|
| Java | 17 |
| Spring Boot | 3.2.11 |
| Spring Web | 3.2.11 |
| Spring Thymeleaf | 3.2.11 |
| Spring Data JPA | 3.2.11 |
| Spring Security | 3.2.11 |
| Spring Mail | 3.2.11 |
| Spring Validation | 3.2.11 |
| Spring Actuator | 3.2.11 |
| Flyway | 9.22.3 |
| H2 | 2.2.224 |
| PostgreSQL JDBC | runtime |
| Lombok | 1.18.30 |

### Flags globales de aplicación

`BunnycureApplication` tiene:

- `@SpringBootApplication`
- `@EnableAsync`
- `@EnableScheduling`
- `@EnableConfigurationProperties`

Implicancias:

- tareas asíncronas para notificaciones,
- schedulers activos en runtime,
- configuración tipada para WhatsApp vía `@ConfigurationProperties`.

---

## 3. Arquitectura actual

### 3.1 Capas

```text
Controllers MVC/REST
  -> DTOs / binding / validación
  -> Services transaccionales
  -> Repositories Spring Data JPA
  -> Entidades JPA + migraciones Flyway
```

### 3.2 Paquetes principales

```text
cl.bunnycure
├─ config
├─ domain
│  ├─ model
│  ├─ enums
│  └─ repository
├─ service
├─ web
│  ├─ controller
│  └─ dto
└─ exception
```

### 3.3 Decisiones técnicas observables

- `spring.jpa.open-in-view=false`
- `ddl-auto=validate` en ambos perfiles
- migraciones separadas para PostgreSQL y H2
- seguridad basada en formulario y sesión
- templates Thymeleaf para UI y mails
- webhook WhatsApp tratado como endpoint público específico

---

## 4. Modelo de dominio

### Entidades principales

| Entidad | Propósito | Comentarios |
|---|---|---|
| `Appointment` | Cita agendada | Estado, fecha, hora, observaciones, flags de notificación |
| `BookingRequest` | Solicitud pública | Antes de convertirse en cita |
| `Customer` | Ficha de clienta | Incluye perfil extendido |
| `ServiceCatalog` | Servicios ofrecidos | Activación/desactivación y orden |
| `User` | Usuario administrativo | Autenticación local/Heroku |
| `AppSettings` | Configuración dinámica | Portal y WhatsApp humano |
| `PasswordResetToken` | Reset de password | Token temporal de un solo uso |
| `WebhookProcessedEvent` | Dedupe persistente | Evita reprocesar eventos webhook |
| `WebhookOperationalEvent` | Trazas operacionales | Eventos de riesgo/calidad/alertas |

### Campos relevantes agregados recientemente

`Customer` y/o `BookingRequest` ya consideran:

- `gender`
- `birthDate`
- `emergencyPhone`
- `healthNotes`
- `notes`

Esto permite que la reserva pública también alimente la ficha de la clienta.

---

## 5. Flujos de negocio implementados

### 5.1 Solicitud pública de reserva

Ruta principal:

- `GET /reservar`
- `POST /reservar/submit`

Flujo:

1. `BookingController` renderiza formulario y settings activos.
2. `BookingRequestDto` valida entrada.
3. `BookingRequestService.create()`:
   - busca o crea `Customer` por teléfono,
   - sincroniza datos de perfil desde la solicitud,
   - crea `BookingRequest` en estado `PENDING`,
   - dispara notificación de recepción si hay email.
4. La notificación puede incluir email y/o WhatsApp según configuración disponible.

Complemento público:

- `POST /api/customers/lookup` permite lookup por teléfono para autocompletar clientas recurrentes.

### 5.2 Aprobación o rechazo de solicitud

Rutas:

- `GET /admin/booking-requests`
- `GET /admin/booking-requests/{id}`
- `POST /admin/booking-requests/{id}/approve`
- `POST /admin/booking-requests/{id}/reject`

En aprobación:

1. Se obtiene la solicitud pendiente.
2. Se busca/crea la clienta por teléfono.
3. Se sincronizan datos del perfil.
4. Se crea `Appointment` con fecha de solicitud y hora decidida por admin.
5. La solicitud pasa a `APPROVED` y se vincula a la cita.
6. `NotificationService.sendAppointmentConfirmation()` dispara correo y WhatsApp.

En rechazo:

1. La solicitud pasa a `REJECTED`.
2. Se guarda el motivo.
3. Se notifica por email/WhatsApp cuando aplica.

### 5.3 Gestión de citas

Rutas reales de agenda:

- `GET /appointments`
- `GET /appointments/new`
- `GET /appointments/new-from-wa`
- `POST /appointments`
- `GET /appointments/{id}`
- `POST /appointments/{id}/edit`
- `POST /appointments/{id}/status`
- `POST /appointments/{id}/notify`
- `POST /appointments/{id}/delete`

La agenda soporta:

- vista `day`, `week` y `month`,
- enlaces de handoff WhatsApp por cita,
- creación rápida desde contexto de WhatsApp,
- reenvío manual de confirmaciones,
- actualización de status,
- borrado con desvinculación defensiva de `BookingRequest`.

### 5.4 Gestión de clientas

Rutas:

- `GET /customers`
- `GET /customers/new`
- `POST /customers`
- `GET /customers/{id}`
- `GET /customers/{id}/edit`
- `POST /customers/{id}/edit`
- `POST /customers/{id}/delete`

Notas:

- El prefijo real es `/customers`, no `/admin/customers`.
- El controller usa resúmenes y búsqueda por texto.

### 5.5 Servicios, settings y usuarios admin

Rutas:

- `GET/POST /admin/services`
- `POST /admin/services/{id}/toggle`
- `POST /admin/services/{id}/delete`
- `GET/POST /admin/settings`
- `GET/POST /admin/users...`

`AdminSettingsController` hoy persiste settings para:

- `booking.enabled`
- WhatsApp humano
- display name del canal humano
- habilitación de handoff
- mensaje al cliente y texto prefill al admin
- template de mensaje de reserva
- bloques horario mañana/tarde/noche y sus toggles

---

## 6. Seguridad y cuentas

### 6.1 Reglas HTTP (`SecurityConfig`)

Se permiten sin autenticación:

- recursos estáticos,
- `/login` y `/login/**`,
- `/forgot-password`, `/reset-password`,
- `/`, `/reservar`, `/reservar/**`, `/reservar/submit`,
- `/api/customers/lookup`,
- `GET/POST /api/webhooks/whatsapp`,
- `/.well-known/**`,
- en `local`: `/h2-console/**`, `/api/test/**`, `/api/webhooks/whatsapp/status`, `/api/webhooks/whatsapp/test`.

Todo lo demás requiere autenticación.

### 6.2 Login y redirección inteligente

`HomeController` revisa el header `Host`:

- si contiene `admin.bunnycure.cl` y el usuario tiene `ROLE_ADMIN`, redirige a `/dashboard`;
- si contiene `admin.bunnycure.cl` y no está autenticado, redirige a `/login`;
- en cualquier otro caso, redirige a `/reservar`.

### 6.3 Password legacy y cambio obligatorio

`PasswordChangeAuthenticationSuccessHandler` revisa si el hash almacenado coincide con passwords legacy conocidas.

Si detecta una clave legacy:

- marca `mustChangePassword` en sesión,
- redirige a `/admin/change-password?required=true`.

`PasswordChangeController` exige:

- confirmación coincidente,
- largo mínimo de 8,
- que no sea una clave bloqueada,
- que la nueva sea distinta de la actual.

### 6.4 Recuperación de contraseña

Flujo implementado:

- `GET/POST /forgot-password`
- `GET/POST /reset-password`

`PasswordResetService`:

- invalida tokens previos activos,
- crea token UUID,
- expira en 30 minutos,
- al resetear marca el token como usado.

### 6.5 Bootstrap admin por perfil

#### Perfil `local`

`DataInitializer`:

- asegura existencia del admin local,
- carga catálogo de servicios inicial,
- inserta clientas de prueba,
- crea settings por defecto de portal/WhatsApp.

#### Perfil `heroku`

`AdminUserInitializer`:

- exige `bunnycure.admin.username` y `bunnycure.admin.password`,
- rechaza password vacía o insegura,
- crea admin si no existe,
- o rota únicamente si detecta password legacy.

---

## 7. Notificaciones y async

### 7.1 `NotificationService`

Responsabilidades observadas:

- confirmación de cita
- cancelación de cita
- recepción de solicitud
- rechazo de solicitud
- recordatorios manuales y automáticos
- correo HTML con Thymeleaf
- disparo de templates WhatsApp vía `WhatsAppService`

La mayoría de estos métodos está anotada con `@Async`.

### 7.2 Email

Configuración base en `application.properties`:

- host/puerto SMTP por variables de entorno,
- timeouts explícitos,
- `bunnycure.mail.enabled` para apagar el canal.

### 7.3 WhatsApp saliente

`WhatsAppService` implementa:

- `sendTextMessage()`
- `sendAppointmentConfirmation()`
- `sendAppointmentCancellation()`
- `sendAppointmentReminder()`
- `sendBookingRequestReceived()`
- `sendBookingRequestRejected()`
- envío genérico de templates

`WhatsAppConfig` concentra:

- token y `phoneId`,
- nombres de templates,
- idioma,
- flags `useTemplateFor...`,
- nombre del negocio.

### 7.4 Handoff humano por WhatsApp

`WhatsAppHandoffService` construye:

- link general al canal humano,
- mensaje visible para cliente,
- link admin -> clienta con texto prellenado desde una solicitud,
- link admin -> clienta con texto prellenado desde una cita.

Tokens soportados en el prefill, según caso:

- `{nombre}`
- `{telefono}`
- `{servicio}`
- `{fecha}`
- `{hora}`
- `{bloque}`

---

## 8. Webhook de WhatsApp

### 8.1 Controller

`WhatsAppWebhookController` expone:

- `GET /api/webhooks/whatsapp` para challenge de Meta
- `POST /api/webhooks/whatsapp` para notificaciones
- `POST /api/webhooks/whatsapp/test` solo en local
- `GET /api/webhooks/whatsapp/status` solo en local

Comportamiento:

- resuelve `X-Hub-Signature-256`, fallback a headers alternativos,
- valida firma a través de `WhatsAppWebhookService`,
- deserializa payload con `ObjectMapper`,
- responde `EVENT_RECEIVED` rápidamente.

### 8.2 Validación de firma

`WhatsAppWebhookService.isSignatureValid(...)`:

- usa HMAC SHA-256,
- compara con `MessageDigest.isEqual`,
- acepta múltiples candidatos `sha256=...` en el header,
- si `WHATSAPP_WEBHOOK_APP_SECRET` está vacío, la validación se considera desactivada de forma explícita.

> Recomendación: en producción configurar siempre `WHATSAPP_WEBHOOK_APP_SECRET`.

### 8.3 Procesamiento de eventos

`WhatsAppWebhookService` maneja al menos:

- mensajes entrantes de texto,
- botones,
- interacciones,
- estados de mensajes,
- cambios de calidad/template,
- account alerts,
- actualizaciones operacionales de número/cuenta.

Además implementa:

- dedupe en memoria con TTL,
- persistencia en `webhook_processed_events`,
- persistencia resumida de eventos operacionales,
- throttling de alertas de riesgo,
- respuesta de handoff humano para mensajes libres cuando el feature está activo.

### 8.4 Mantenimiento de eventos operacionales

`WebhookOperationalEventMaintenanceService` ejecuta un scheduler configurable por:

- `WHATSAPP_WEBHOOK_OP_EVENTS_CLEANUP_ENABLED`
- `WHATSAPP_WEBHOOK_OP_EVENTS_RETENTION_DAYS`
- `WHATSAPP_WEBHOOK_OP_EVENTS_CLEANUP_CRON`

Objetivo:

- borrar eventos operacionales antiguos de la tabla `webhook_operational_events`.

---

## 9. Scheduling y recordatorios

### `ReminderScheduler`

Cron actuales:

- `0 0 9 * * *` -> recordatorios para citas de mañana
- `0 0 */2 * * *` -> recordatorios para citas dentro de 2 horas

### Flujos manuales adicionales

Controllers relevantes:

- `AdminRemindersController`
- `AdminAppointmentController`

Funciones expuestas:

- envío manual masivo del día,
- envío individual por cita,
- página de estadísticas/pendientes.

---

## 10. Persistencia y migraciones

### 10.1 Estrategia

- Perfil `local` -> `db/migration-h2`
- Perfil `heroku` -> `db/migration`
- Ambas carpetas deben mantenerse en paridad.

### 10.2 Estado actual de migraciones

Actualmente existen migraciones `V1` a `V24` en ambos motores, incluyendo:

- schema inicial
- catálogo de servicios
- flags de recordatorio
- soporte de usuarios
- tabla de settings
- solicitudes de reserva
- secuencias y fixes de alineación
- reset y forzado de password admin
- tokens de recuperación
- email nullable en clientas
- campos de perfil en booking requests/clientas
- tablas para webhook procesado y eventos operacionales
- corrección de constraint de status en appointments

### 10.3 Test de paridad

`MigrationParityTest` verifica:

- existencia de ambas carpetas,
- mismo set de scripts entre PostgreSQL y H2,
- ausencia de versiones Flyway duplicadas.

---

## 11. Perfiles y configuración

### 11.1 `application.properties`

Base común:

- `server.port=${PORT:8080}`
- JPA en modo `validate`
- mail por variables de entorno
- configuración de WhatsApp Cloud API
- configuración de webhook
- `management.endpoints.web.exposure.include=health,info`

### 11.2 `application-local.properties`

Comportamiento:

- H2 file-based
- H2 console habilitada
- Flyway activo sobre `db/migration-h2`
- Thymeleaf cache off
- DevTools habilitado
- logging más detallado

### 11.3 `application-heroku.properties`

Comportamiento:

- dialecto PostgreSQL
- Flyway sobre `db/migration`
- mail parametrizado por env vars
- bootstrap admin obligatorio por variables
- soporte para reverse proxy: `server.forward-headers-strategy=framework`

### 11.4 `HerokuDataSourceConfig`

Lee `DATABASE_URL` y la transforma a JDBC PostgreSQL.

Si falta o viene malformada, lanza `IllegalStateException` durante el arranque.

---

## 12. Endpoints relevantes

### Públicos

| Ruta | Método | Observación |
|---|---|---|
| `/` | GET | Redirección por host |
| `/reservar` | GET | Portal de reserva |
| `/reservar/submit` | POST | Crea `BookingRequest` |
| `/api/customers/lookup` | POST | Lookup público por teléfono |
| `/login` | GET/POST | Login form |
| `/forgot-password` | GET/POST | Inicio recovery |
| `/reset-password` | GET/POST | Cambio vía token |
| `/api/webhooks/whatsapp` | GET/POST | Webhook Meta |
| `/.well-known/appspecific/com.chrome.devtools.json` | GET | Evita 404 de DevTools |

### Autenticados

| Ruta base | Comentario |
|---|---|
| `/dashboard` | Dashboard principal |
| `/appointments` | Agenda y edición de citas |
| `/customers` | Gestión de clientas |
| `/admin/booking-requests` | Solicitudes de reserva |
| `/admin/services` | Catálogo de servicios |
| `/admin/settings` | Settings del negocio/WhatsApp |
| `/admin/users` | Gestión de admins |
| `/admin/reminders` | Recordatorios |
| `/admin/change-password` | Cambio password usuario actual |

### Técnicos

| Ruta | Comentario |
|---|---|
| `/actuator/health` | Health básico |
| `/actuator/info` | Info expuesta si se configura |
| `/h2-console/**` | Solo `local` |
| `/api/webhooks/whatsapp/status` | Solo `local` |
| `/api/webhooks/whatsapp/test` | Solo `local` |

---

## 13. Comandos operativos frecuentes

### Build

```bat
mvnw.cmd clean verify
```

### Arranque local

```bat
set SPRING_PROFILES_ACTIVE=local
set BUNNYCURE_ADMIN_USERNAME=admin
set BUNNYCURE_ADMIN_PASSWORD=TU_PASSWORD_LOCAL_SEGURA
mvnw.cmd spring-boot:run
```

### Tests

```bat
mvnw.cmd test
```

### Package

```bat
mvnw.cmd clean package
```

---

## 14. Variables de entorno técnicas

### Seguridad

- `BUNNYCURE_ADMIN_USERNAME`
- `BUNNYCURE_ADMIN_PASSWORD`

### Runtime / despliegue

- `SPRING_PROFILES_ACTIVE`
- `PORT`
- `JAVA_OPTS`
- `DATABASE_URL`

### Mail

- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `MAIL_ENABLED`

### WhatsApp

- `WHATSAPP_NUMBER`
- `WHATSAPP_API_TOKEN`
- `WHATSAPP_PHONE_ID`
- `WHATSAPP_TEMPLATE_CONFIRMACION`
- `WHATSAPP_TEMPLATE_RECORDATORIO`
- `WHATSAPP_TEMPLATE_CANCELACION`
- `WHATSAPP_TEMPLATE_AGENDA`
- `WHATSAPP_TEMPLATE_RECHAZO`
- `WHATSAPP_TEMPLATE_LANG`
- `WHATSAPP_USE_TEMPLATE_CONFIRMATION`
- `WHATSAPP_USE_TEMPLATE_REMINDER`
- `WHATSAPP_USE_TEMPLATE_CANCELLATION`
- `WHATSAPP_USE_TEMPLATE_BOOKING_REQUEST`
- `WHATSAPP_USE_TEMPLATE_BOOKING_REJECTION`
- `WHATSAPP_BUSINESS_NAME`

### Webhook WhatsApp

- `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
- `WHATSAPP_WEBHOOK_APP_SECRET`
- `WHATSAPP_WEBHOOK_ALERT_ADMIN`
- `WHATSAPP_WEBHOOK_OP_EVENTS_CLEANUP_ENABLED`
- `WHATSAPP_WEBHOOK_OP_EVENTS_RETENTION_DAYS`
- `WHATSAPP_WEBHOOK_OP_EVENTS_CLEANUP_CRON`

---

## 15. Suite de pruebas existente

Pruebas identificadas en `src/test/java`:

- `BunnycureApplicationTests`
- `MigrationParityTest`
- `AppointmentServiceTest`
- `AppSettingsServiceTest`
- `PasswordResetServiceTest`
- `ServiceCatalogServiceTest`
- `WebhookOperationalEventMaintenanceServiceTest`
- `WhatsAppHandoffServiceTest`
- `WhatsAppServiceTest`
- `WhatsAppWebhookServiceTest`
- `WhatsAppWebhookControllerTest`

Cobertura funcional visible:

- servicios core,
- reset de password,
- webhook controller/service,
- handoff WhatsApp,
- limpieza de eventos operacionales,
- paridad de migraciones.

---

## 16. Observaciones de operación

- El README anterior mezclaba rutas antiguas; ahora quedan documentados los prefijos reales del código actual.
- La gestión de clientas usa `/customers`, no `/admin/customers`.
- La gestión principal de agenda usa `/appointments`, mientras que `/admin/appointments/...` queda para recordatorios administrativos complementarios.
- El soporte de despliegue Heroku existe en código/configuración, pero la documentación no asume estado productivo activo.
- En producción no conviene depender de valores por defecto para admin o webhook; deben sobrescribirse por variables seguras.

---

## 17. Documentación relacionada en `docs-dev/`

- `docs-dev/DESARROLLO_LOCAL.md`
- `docs-dev/README_WHATSAPP.md`
- `docs-dev/README_MIGRATIONS.md`
- `docs-dev/CHECKLIST_HEROKU.md`
- `docs-dev/CAMBIO_OBLIGATORIO_PASSWORD.md`
- `docs-dev/WHATSAPP_WEBHOOK_SETUP.md`
- `docs-dev/INDICE_VARIABLES_HEROKU.md`

---

## 18. Recomendaciones de mantenimiento

1. Mantener sincronizadas ambas carpetas de migración en cada cambio de esquema.
2. No documentar ni commitear secretos reales; usar solo nombres de variables y placeholders.
3. Probar el webhook con firma activa antes de promoción a producción.
4. Mantener rotación obligatoria para cualquier password bootstrap temporal.
5. Si se agregan nuevos eventos de WhatsApp, registrar también su estrategia de dedupe/operación.
