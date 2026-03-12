# 💅 BunnyCure

Sistema web para gestión de reservas, agenda y seguimiento de clientas de un centro estético, construido con Spring Boot 3.2.11, Thymeleaf, JPA/Flyway y soporte de notificaciones por email y WhatsApp.

> Documento actualizado al **12 de marzo de 2026**. Este README evita publicar credenciales, secretos o contraseñas de ningún entorno.

---

## ✅ Estado actual del proyecto

Actualmente BunnyCure incluye:

- **Portal público de reservas** en `/reservar`
- **Panel administrativo** con dashboard, agenda, clientes, servicios, usuarios y settings
- **Autenticación con Spring Security** y contraseñas con BCrypt
- **Cambio obligatorio de contraseña** cuando el admin inicia con una clave legacy/insegura
- **Recuperación de contraseña por email** (`/forgot-password` y `/reset-password`)
- **Notificaciones asíncronas** por email y WhatsApp
- **Recordatorios automáticos** programados por scheduler
- **Integración con WhatsApp Cloud API** para envío y recepción de eventos
- **Webhook de WhatsApp** con validación de firma HMAC cuando se configura `WHATSAPP_WEBHOOK_APP_SECRET`
- **Derivación a atención humana por WhatsApp** configurable desde admin settings
- **Persistencia por perfiles**: H2 local y PostgreSQL para despliegue tipo Heroku
- **Migraciones Flyway sincronizadas** entre `db/migration` y `db/migration-h2`

---

## 🧩 Módulos funcionales principales

### Portal público

- Formulario de reserva en `/reservar`
- Creación de `BookingRequest`
- Lookup público de clienta por teléfono en `/api/customers/lookup`
- Bloques horarios configurables desde settings
- Mensajes de derivación a WhatsApp humano configurables

### Backoffice administrativo

- Dashboard con métricas del día y solicitudes pendientes
- Aprobación y rechazo de solicitudes de reserva
- Gestión de citas en vistas **día / semana / mes**
- Gestión de clientas con perfil ampliado
- Gestión de servicios y usuarios admin
- Configuración del portal y de la experiencia de WhatsApp
- Envío manual de notificaciones y recordatorios

### Seguridad y cuentas

- Login form con redirección por host en `/`
- Flujo de cambio obligatorio de contraseña para claves legacy
- Recuperación de contraseña con token de un solo uso
- Gestión de usuarios administrativos desde `/admin/users`

### Notificaciones y automatizaciones

- Email: confirmación, cancelación, recordatorios y recuperación de contraseña
- WhatsApp: confirmación, recordatorio, cancelación, recepción y rechazo de solicitud
- Scheduler diario a las 09:00 para recordatorios del día siguiente
- Scheduler cada 2 horas para recordatorios cercanos

### Webhook de WhatsApp

- Verificación Meta por `GET /api/webhooks/whatsapp`
- Recepción de eventos por `POST /api/webhooks/whatsapp`
- Dedupe de eventos procesados
- Registro de eventos operacionales y limpieza programada
- Endpoints auxiliares de diagnóstico solo en local:
  - `/api/webhooks/whatsapp/status`
  - `/api/webhooks/whatsapp/test`

---

## 🏗️ Stack tecnológico

| Componente | Versión | Uso |
|---|---:|---|
| Java | 17 | Runtime principal |
| Spring Boot | 3.2.11 | Framework base |
| Spring Web + Thymeleaf | 3.2.11 | MVC SSR |
| Spring Data JPA | 3.2.11 | Persistencia |
| Spring Security | 3.2.11 | Login y autorización |
| Spring Mail | 3.2.11 | Correos transaccionales |
| Spring Actuator | 3.2.11 | Health/info |
| Flyway | 9.22.3 | Migraciones |
| PostgreSQL Driver | runtime | Producción |
| H2 | 2.2.224 | Desarrollo local |
| Lombok | 1.18.30 | Boilerplate |

---

## 📁 Estructura resumida

```text
src/main/java/cl/bunnycure/
├─ config/      Configuración de seguridad, datasource, schedulers e inicialización
├─ domain/      Entidades, enums y repositorios
├─ service/     Lógica de negocio, notificaciones y webhook
├─ web/         Controllers MVC/REST y DTOs
└─ exception/   Manejo de errores

src/main/resources/
├─ application*.properties
├─ db/migration/      Migraciones PostgreSQL
├─ db/migration-h2/   Migraciones H2
├─ templates/         Vistas Thymeleaf
└─ static/            CSS, JS, imágenes
```

---

## 🚀 Puesta en marcha local

### 1) Compilar

```bat
mvnw.cmd clean verify
```

### 2) Definir perfil y credenciales por variables de entorno

> Recomendado: definir explícitamente las variables de admin también en local. Si el entorno local arranca con una contraseña legacy, el sistema obligará al cambio en el primer login.

```bat
set SPRING_PROFILES_ACTIVE=local
set BUNNYCURE_ADMIN_USERNAME=admin
set BUNNYCURE_ADMIN_PASSWORD=TU_PASSWORD_LOCAL_SEGURA
```

Si también quieres probar correo o WhatsApp en local, agrega las variables correspondientes antes de iniciar.

### 3) Ejecutar

```bat
mvnw.cmd spring-boot:run
```

### 4) URLs útiles

- Portal público: `http://localhost:8080/reservar`
- Login admin: `http://localhost:8080/login`
- H2 Console: `http://localhost:8080/h2-console`
- Health: `http://localhost:8080/actuator/health`

---

## ⚙️ Perfiles soportados

### `local`

- Base H2 en archivo: `target/bunnycure-local`
- Flyway usando `classpath:db/migration-h2`
- H2 Console habilitada
- Thymeleaf sin caché
- Logging más verboso

### `heroku`

- PostgreSQL obtenido desde `DATABASE_URL`
- Datasource armado por `HerokuDataSourceConfig`
- Flyway usando `classpath:db/migration`
- Bootstrap admin validado por `AdminUserInitializer`
- `Procfile` listo para arrancar con perfil `heroku`

> El proyecto mantiene soporte de despliegue tipo Heroku, pero este README no asume ni afirma un despliegue productivo actualmente activo.

---

## 🔐 Variables de entorno relevantes

### Obligatorias o altamente recomendadas en local

| Variable | Uso |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Activa `local` o `heroku` |
| `BUNNYCURE_ADMIN_USERNAME` | Usuario bootstrap de administración |
| `BUNNYCURE_ADMIN_PASSWORD` | Password bootstrap de administración |

### Base de datos / runtime

| Variable | Uso |
|---|---|
| `PORT` | Puerto HTTP |
| `DATABASE_URL` | PostgreSQL para perfil `heroku` |
| `JAVA_OPTS` | Ajustes JVM en despliegue |

### Email

| Variable | Uso |
|---|---|
| `MAIL_HOST` | Host SMTP |
| `MAIL_PORT` | Puerto SMTP |
| `MAIL_USERNAME` | Usuario SMTP |
| `MAIL_PASSWORD` | Password SMTP |
| `MAIL_FROM` | Remitente |
| `MAIL_ENABLED` | Habilita/deshabilita envío |

### WhatsApp Cloud API

| Variable | Uso |
|---|---|
| `WHATSAPP_API_TOKEN` | Token de acceso a Cloud API |
| `WHATSAPP_PHONE_ID` | Phone number ID |
| `WHATSAPP_NUMBER` | Número humano / fallback visible |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | Token de verificación Meta |
| `WHATSAPP_WEBHOOK_APP_SECRET` | Secreto para validar firma HMAC |
| `WHATSAPP_TEMPLATE_CONFIRMACION` | Template confirmación |
| `WHATSAPP_TEMPLATE_RECORDATORIO` | Template recordatorio |
| `WHATSAPP_TEMPLATE_CANCELACION` | Template cancelación |
| `WHATSAPP_TEMPLATE_AGENDA` | Template solicitud recibida |
| `WHATSAPP_TEMPLATE_RECHAZO` | Template solicitud rechazada |
| `WHATSAPP_TEMPLATE_LANG` | Idioma template |
| `WHATSAPP_USE_TEMPLATE_CONFIRMATION` | Uso de template en confirmación |
| `WHATSAPP_USE_TEMPLATE_REMINDER` | Uso de template en recordatorio |
| `WHATSAPP_USE_TEMPLATE_CANCELLATION` | Uso de template en cancelación |
| `WHATSAPP_USE_TEMPLATE_BOOKING_REQUEST` | Uso de template al recibir solicitud |
| `WHATSAPP_USE_TEMPLATE_BOOKING_REJECTION` | Uso de template al rechazar solicitud |
| `WHATSAPP_BUSINESS_NAME` | Nombre comercial usado en mensajes |
| `WHATSAPP_WEBHOOK_ALERT_ADMIN` | Alertas admin por eventos de riesgo |
| `WHATSAPP_WEBHOOK_OP_EVENTS_CLEANUP_ENABLED` | Limpieza de eventos operacionales |
| `WHATSAPP_WEBHOOK_OP_EVENTS_RETENTION_DAYS` | Retención de eventos |
| `WHATSAPP_WEBHOOK_OP_EVENTS_CLEANUP_CRON` | Cron de limpieza |

---

## 🔌 Rutas principales

### Públicas

| Ruta | Descripción |
|---|---|
| `/` | Redirección por host/autenticación |
| `/reservar` | Portal público de reservas |
| `/reservar/submit` | Creación de solicitud |
| `/api/customers/lookup` | Lookup público de clienta por teléfono |
| `/login` | Login |
| `/forgot-password` | Solicitud de recuperación |
| `/reset-password` | Restablecimiento con token |
| `/api/webhooks/whatsapp` | Verificación y recepción del webhook |
| `/.well-known/appspecific/com.chrome.devtools.json` | Respuesta técnica para DevTools |

### Requieren autenticación

| Ruta | Descripción |
|---|---|
| `/dashboard` | Dashboard principal |
| `/appointments` | Agenda de citas |
| `/customers` | Gestión de clientas |
| `/admin/booking-requests` | Solicitudes de reserva |
| `/admin/services` | Catálogo de servicios |
| `/admin/settings` | Configuración de negocio y WhatsApp |
| `/admin/users` | Usuarios administrativos |
| `/admin/reminders` | Gestión de recordatorios |
| `/admin/change-password` | Cambio de contraseña del usuario actual |

---

## 📱 Cambios recientes ya incorporados

Los README quedan alineados con estos cambios del proyecto:

- Integración real de **WhatsApp Cloud API** con templates configurables
- **Webhook seguro** con soporte de firma `X-Hub-Signature-256`
- Persistencia de **eventos procesados** y **eventos operacionales** del webhook
- **Limpieza programada** de eventos operacionales
- **Derivación a atención humana** por WhatsApp configurable desde settings
- **Recuperación de contraseña** por token vía email
- **Cambio obligatorio de contraseña** para claves legacy
- **Campos de perfil** adicionales en solicitudes/clientas: género, fecha de nacimiento, teléfono de emergencia y notas
- Vistas de agenda **día / semana / mes**
- Paridad de migraciones entre H2 y PostgreSQL validada por tests

---

## 🧪 Validación y calidad

El repositorio incluye pruebas para áreas críticas, entre ellas:

- `WhatsAppWebhookControllerTest`
- `WhatsAppWebhookServiceTest`
- `WhatsAppServiceTest`
- `WhatsAppHandoffServiceTest`
- `PasswordResetServiceTest`
- `AppointmentServiceTest`
- `MigrationParityTest`

Comando habitual:

```bat
mvnw.cmd test
```

---

## 📚 Documentación complementaria

- `README_TECNICO.md`: arquitectura y operación técnica detallada
- `docs-dev/DESARROLLO_LOCAL.md`: guía de desarrollo local
- `docs-dev/README_WHATSAPP.md`: integración WhatsApp
- `docs-dev/README_MIGRATIONS.md`: estrategia de migraciones
- `docs-dev/CHECKLIST_HEROKU.md`: checklist de despliegue
- `docs-dev/CAMBIO_OBLIGATORIO_PASSWORD.md`: flujo de rotación de password

---

## 📦 Build y despliegue

### Build

```bat
mvnw.cmd clean package
```

### Soporte Heroku

El proyecto ya incluye:

- `Procfile`
- `system.properties`
- perfil `heroku`
- parsing de `DATABASE_URL`

Antes de desplegar, configura todas las variables sensibles en el proveedor y no las subas al repositorio.

---

## 🔒 Nota de seguridad

- No publiques usuarios, contraseñas, tokens, secretos SMTP ni secretos de webhook en commits, issues o documentación.
- Aunque exista un entorno local, **define tus variables por ambiente** y rota cualquier valor temporal apenas se use.
- Para producción, configura siempre `WHATSAPP_WEBHOOK_APP_SECRET` y un `BUNNYCURE_ADMIN_PASSWORD` fuerte.
