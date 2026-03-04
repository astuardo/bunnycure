# 💅 BunnyCure – Sistema de Gestión de Citas Estéticas

> **Sistema integral de reserva y gestión de citas estéticas** con autenticación segura, notificaciones automáticas por email y recordatorios programados. Arquitectura escalable basada en Spring Boot 3.2.11 con soporte para múltiples entornos (local H2 y producción PostgreSQL).

---

## 📋 Tabla de Contenidos

1. [Propósito y Solución de Ingeniería](#propósito-y-solución-de-ingeniería)
2. [Stack Tecnológico](#stack-tecnológico)
3. [Arquitectura de Software](#arquitectura-de-software)
4. [Estructura de Carpetas](#estructura-de-carpetas)
5. [Configuración y Runtime](#configuración-y-runtime)
6. [Endpoints y Uso](#endpoints-y-uso)
7. [Guía de Desarrollo](#guía-de-desarrollo)
8. [Variables de Entorno](#variables-de-entorno)
9. [Despliegue](#despliegue)

---

## 🎯 Propósito y Solución de Ingeniería

BunnyCure es una solución empresarial de **gestión integral de citas estéticas** diseñada para salones y centros de belleza. El sistema proporciona:

- **Portal Público**: Clientes pueden crear solicitudes de reserva sin autenticación
- **Panel Administrativo**: Gestión completa de citas, clientes, servicios y configuración
- **Autenticación Segura**: Spring Security con BCrypt password encoding
- **Notificaciones**: Envío automático de emails de confirmación, cancelación y recordatorios
- **Programación de Tareas**: Recordatorios automáticos mediante Spring Scheduling
- **Persistencia Multi-Ambiente**: H2 local, PostgreSQL producción
- **Migraciones Controladas**: Flyway para versionamiento de esquema

**Diferenciadores técnicos:**
- Arquitectura de capas limpias con separación responsabilidades (Controllers → Services → Repositories)
- DTOs con validación exhaustiva (Jakarta Validation)
- Async/Scheduling para operaciones no-bloqueantes
- Multi-tenancy básica por subdominio (admin.bunnycure.cl vs reservar.bunnycure.cl)

---

## 📦 Stack Tecnológico

| Componente | Versión | Propósito |
|---|---|---|
| **Java** | 17 LTS | JVM target |
| **Spring Boot** | 3.2.11 | Framework principal |
| **Spring Data JPA** | 3.2.11 | ORM y acceso a datos |
| **Spring Security** | 3.2.11 | Autenticación y autorización |
| **Spring Mail** | 3.2.11 | Envío de emails |
| **Spring Web** | 3.2.11 | MVC y controllers |
| **Spring Actuator** | 3.2.11 | Health checks y métricas |
| **Thymeleaf** | 3.2.11 | Template engine (SSR) |
| **Thymeleaf Security** | 6.x | Integración Thymeleaf + Spring Security |
| **Flyway** | 9.22.3 | Migraciones de base de datos |
| **PostgreSQL Driver** | Runtime | Driver JDBC PostgreSQL |
| **H2 Database** | 2.2.224 | BD en memoria/archivo local |
| **Lombok** | 1.18.30 | Eliminación boilerplate |
| **Jakarta Validation** | 3.2.11 | Validaciones de datos |
| **Maven Compiler** | 17 | Compilación Java 17 |
| **Spring Boot Maven Plugin** | 3.2.11 | Build y packaging |

**Perfil de Compilación:**
- Source/Target: Java 17
- Encoding: UTF-8
- Anotación de Lombok automática mediante maven-compiler-plugin

---

## 🏗️ Arquitectura de Software

### 1. **Patrón de Capas**

```
┌─────────────────────────────────────────────────────────────┐
│                    WEB LAYER (Controllers)                   │
│  HomeController, LoginController, DashboardController...    │
└────────────────────────┬────────────────────────────────────┘
                         │ Requests/Responses
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    DTO LAYER (Data Transfer Objects)         │
│  BookingRequestDto, AppointmentDto, CustomerDto...          │
└────────────────────────┬────────────────────────────────────┘
                         │ Transformation
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                SERVICE LAYER (Business Logic)                │
│  AppointmentService, BookingRequestService...              │
│  - Transacciones                                             │
│  - Validaciones                                              │
│  - Operaciones Async (@Async)                              │
└────────────────────────┬────────────────────────────────────┘
                         │ Data Queries
                         ▼
┌─────────────────────────────────────────────────────────────┐
│          REPOSITORY LAYER (Data Persistence)                 │
│  AppointmentRepository, BookingRequestRepository...          │
│  (Spring Data JPA)                                           │
└────────────────────────┬────────────────────────────────────┘
                         │ SQL / JPA Queries
                         ▼
┌─────────────────────────────────────────────────────────────┐
│               DOMAIN LAYER (Entity Models)                    │
│  Appointment, BookingRequest, Customer, User...              │
│  - @Entity con JPA annotations                              │
│  - Relaciones (ManyToOne, OneToMany)                        │
│  - Enums (AppointmentStatus, BookingRequestStatus)          │
└─────────────────────────────────────────────────────────────┘
```

### 2. **Flujo de Datos – Ejemplo: Crear Solicitud de Reserva**

```
[Cliente] → POST /reservar/submit
           ↓
[BookingRequestController.submitRequest()]
           ↓
[BindingResult validation] ← BookingRequestDto
           ↓
[BookingRequestService.create()]
           ↓
[BookingRequestRepository.save()] → DB
           ↓
[NotificationService.sendAcknowledgement()] @Async
           ↓
[Confirmation Email] → SMTP
```

### 3. **Flujo de Datos – Ejemplo: Aprobar Solicitud**

```
[Admin] → POST /admin/booking-requests/{id}/approve
         ↓
[BookingRequestController.approve()]
         ↓
[BookingApprovalDto validation]
         ↓
[BookingRequestService.approve()]
         ├─ Create Appointment
         ├─ Update BookingRequest status
         ├─ Save to DB
         └─ Call NotificationService.sendAppointmentConfirmation() @Async
         ↓
[NotificationService.sendAppointmentConfirmation()]
         ↓
[Email Template: confirmation.html]
         ↓
[Send via JavaMailSender] → SMTP
```

### 4. **Transacciones y Consistencia**

- **@Transactional(readOnly = true)**: En métodos de búsqueda (Services)
- **@Transactional**: En métodos de creación/actualización
- **JPA Open-in-View**: Deshabilitado (`spring.jpa.open-in-view=false`) para mejor control
- **Lazy Loading**: FetchType.EAGER en relaciones críticas (Customer, Service)

### 5. **Seguridad**

```
┌─────────────────────────────────────────────────┐
│        SecurityFilterChain (Spring Security)     │
├─────────────────────────────────────────────────┤
│ ✓ Static assets (/css, /js, /images)            │
│ ✓ Login endpoints                               │
│ ✓ Public booking portal (/, /reservar)          │
│ ✓ .well-known metadata                          │
│ ✗ Admin endpoints (requieren ROLE_ADMIN)        │
│ ✗ Dashboard (requieren autenticación)           │
└─────────────────────────────────────────────────┘
```

**Credenciales de Administrador:**
- Username: `admin` (configurable: `bunnycure.admin.username`)
- Password: `` (configurable: `bunnycure.admin.password`)
- Encoding: BCrypt

### 6. **Notificaciones Asincrónicas**

```
@EnableAsync en BunnycureApplication
         ↓
[Service method] @Async
         ├─ NotificationService.sendConfirmation()
         ├─ NotificationService.sendCancellationNotice()
         ├─ NotificationService.sendReminder()
         └─ (Ejecutan en thread pool paralelo)

ThreadPool Config:
  - Core size: 2 threads
  - Max size: 5 threads
```

### 7. **Programación de Tareas**

```
@EnableScheduling en BunnycureApplication
         ↓
[ReminderScheduler]
  ├─ @Scheduled(cron = "0 0 9 * * ?") → Daily 9 AM
  ├─ Busca citas confirmadas para mañana
  ├─ Envía reminders @Async
  └─ Marca notificationSent = true
```

---

## 📁 Estructura de Carpetas

```
bunnycure/
├── src/main/
│   ├── java/cl/bunnycure/
│   │   ├── BunnycureApplication.java
│   │   │   └── Entry point, @EnableAsync, @EnableScheduling
│   │   │
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   │   └── Spring Security setup, password encoder, filter chain
│   │   │   ├── MailConfig.java
│   │   │   │   └── JavaMailSender bean configuration
│   │   │   ├── AppAsyncConfig.java
│   │   │   │   └── ThreadPoolExecutor para @Async
│   │   │   ├── SchedulingConfig.java
│   │   │   │   └── Task Scheduler configuration
│   │   │   ├── ReminderScheduler.java
│   │   │   │   └── Cron jobs para recordatorios
│   │   │   └── DataInitializer.java
│   │   │       └── Inicialización de datos al startup (admin user, etc.)
│   │   │
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Appointment.java
│   │   │   │   │   └── @Entity, relaciones (Customer, ServiceCatalog, status enum)
│   │   │   │   ├── BookingRequest.java
│   │   │   │   │   └── @Entity, solicitudes pendientes de aprobación
│   │   │   │   ├── Customer.java
│   │   │   │   │   └── @Entity, información de clientas
│   │   │   │   ├── ServiceCatalog.java
│   │   │   │   │   └── @Entity, catálogo de servicios ofrecidos
│   │   │   │   ├── User.java
│   │   │   │   │   └── @Entity, cuentas administrativas
│   │   │   │   └── AppSettings.java
│   │   │   │       └── @Entity, configuración de aplicación
│   │   │   │
│   │   │   ├── enums/
│   │   │   │   ├── AppointmentStatus.java
│   │   │   │   │   └── PENDING, CONFIRMED, COMPLETED, CANCELLED
│   │   │   │   ├── BookingRequestStatus.java
│   │   │   │   │   └── PENDING, APPROVED, REJECTED
│   │   │   │   └── ServiceType.java
│   │   │   │       └── Categorías de servicios
│   │   │   │
│   │   │   └── repository/
│   │   │       ├── AppointmentRepository.java (extends JpaRepository)
│   │   │       ├── BookingRequestRepository.java
│   │   │       ├── CustomerRepository.java
│   │   │       ├── ServiceCatalogRepository.java
│   │   │       └── UserRepository.java
│   │   │
│   │   ├── service/
│   │   │   ├── AppointmentService.java
│   │   │   │   └── Lógica de citas (crear, actualizar, buscar, cancelar)
│   │   │   ├── BookingRequestService.java
│   │   │   │   └── Gestión de solicitudes (crear, aprobar, rechazar)
│   │   │   ├── CustomerService.java
│   │   │   │   └── Gestión de clientas
│   │   │   ├── ServiceCatalogService.java
│   │   │   │   └── Gestión de servicios
│   │   │   ├── UserService.java
│   │   │   │   └── Gestión de usuarios administrativos
│   │   │   ├── AppSettingsService.java
│   │   │   │   └── Configuración global de la app
│   │   │   ├── NotificationService.java
│   │   │   │   └── Email, reminders, confirmaciones @Async
│   │   │   ├── AppointmentReminderService.java
│   │   │   │   └── Lógica de recordatorios
│   │   │   └── [Más servicios según necesidad]
│   │   │
│   │   ├── web/
│   │   │   ├── controller/
│   │   │   │   ├── HomeController.java
│   │   │   │   │   └── GET /, redirección inteligente por subdominio
│   │   │   │   ├── LoginController.java
│   │   │   │   │   └── GET/POST /login
│   │   │   │   ├── DashboardController.java
│   │   │   │   │   └── GET /dashboard (admin only)
│   │   │   │   ├── BookingRequestController.java
│   │   │   │   │   └── POST /reservar/submit, /admin/booking-requests/*
│   │   │   │   ├── AppointmentController.java
│   │   │   │   │   └── Gestión de citas cliente
│   │   │   │   ├── AdminAppointmentController.java
│   │   │   │   │   └── /admin/appointments/*, recordatorios, edición
│   │   │   │   ├── CustomerController.java
│   │   │   │   │   └── /admin/customers/*
│   │   │   │   ├── ServiceCatalogController.java
│   │   │   │   │   └── /admin/services/*
│   │   │   │   ├── AdminSettingsController.java
│   │   │   │   │   └── /admin/settings
│   │   │   │   ├── AdminRemindersController.java
│   │   │   │   │   └── /admin/reminders
│   │   │   │   ├── UserController.java
│   │   │   │   │   └── /admin/users/*, gestión de admins
│   │   │   │   ├── WellKnownController.java
│   │   │   │   │   └── /.well-known/* (OpenID, ACME, etc.)
│   │   │   │   └── BaseController.java
│   │   │   │       └── Clase base con métodos comunes
│   │   │   │
│   │   │   └── dto/
│   │   │       ├── BookingRequestDto.java
│   │   │       │   └── Validaciones: @NotBlank, @Email, @Pattern (teléfono)
│   │   │       ├── BookingApprovalDto.java
│   │   │       │   └── Para aprobar solicitudes
│   │   │       ├── AppointmentDto.java
│   │   │       │   └── Transfer de datos de citas
│   │   │       ├── CustomerDto.java
│   │   │       │   └── Transfer de datos de clientas
│   │   │       ├── ServiceCatalogDto.java
│   │   │       │   └── Transfer de datos de servicios
│   │   │       └── [Más DTOs según necesidad]
│   │   │
│   │   └── exception/
│   │       ├── ResourceNotFoundException.java
│   │       │   └── Excepción para recursos no encontrados
│   │       └── GlobalExceptionHandler.java
│   │           └── @ControllerAdvice, manejo centralizado de errores
│   │
│   └── resources/
│       ├── application.properties
│       │   └── Configuración base (puerto, JPA, mail, Spring Security)
│       │
│       ├── application-local.properties
│       │   └── Perfil local (H2, console, logging verbose, admin local)
│       │
│       ├── application-heroku.properties
│       │   └── Perfil Heroku (PostgreSQL, URL dinámica, mail SMTP)
│       │
│       ├── templates/
│       │   ├── fragments/
│       │   │   ├── header.html → Navbar compartida
│       │   │   ├── footer.html → Pie compartida
│       │   │   └── [Fragments reutilizables]
│       │   │
│       │   ├── mail/
│       │   │   ├── confirmation.html
│       │   │   │   └── Template de email de confirmación de cita
│       │   │   ├── cancellation.html
│       │   │   │   └── Template de email de cancelación
│       │   │   ├── reminder.html
│       │   │   │   └── Template de email de recordatorio
│       │   │   └── [Más templates de email]
│       │   │
│       │   ├── login.html
│       │   │   └── Página de login con CSS responsive
│       │   │
│       │   ├── dashboard.html
│       │   │   └── Dashboard administrativo principal
│       │   │
│       │   └── admin/
│       │       ├── appointments/
│       │       │   ├── list.html → Listado de citas
│       │       │   ├── detail.html → Detalle y edición
│       │       │   └── reminders.html → Gestión de recordatorios
│       │       │
│       │       ├── booking-requests/
│       │       │   ├── list.html → Solicitudes pendientes y aprobadas
│       │       │   └── detail.html → Detalle con formulario de aprobación
│       │       │
│       │       ├── customers/
│       │       │   ├── list.html
│       │       │   └── detail.html
│       │       │
│       │       ├── services/
│       │       │   ├── list.html
│       │       │   └── form.html
│       │       │
│       │       ├── users/
│       │       │   ├── list.html
│       │       │   └── form.html
│       │       │
│       │       └── settings.html → Configuración global
│       │
│       ├── static/
│       │   ├── css/
│       │   │   ├── style.css → Estilos base
│       │   │   ├── booking.css → Estilos del portal de reservas
│       │   │   └── admin.css → Estilos administrativos
│       │   │
│       │   ├── js/
│       │   │   ├── booking.js → Lógica del formulario de reserva
│       │   │   ├── admin.js → Funciones administrativas
│       │   │   └── [Más scripts]
│       │   │
│       │   └── images/ → Assets estáticos (logos, iconos)
│       │
│       └── db/migration/
│           ├── V1__initial_schema.sql → Schema inicial (appointments, customers)
│           ├── V2__service_catalog.sql → Tabla de servicios
│           ├── V3__add_reminder_sent.sql → Campo reminder_sent en appointments
│           ├── V4__add_confirmed_status.sql → Status CONFIRMED en appointments
│           ├── V5__create_users_table.sql → Tabla de usuarios
│           ├── V6__create_app_settings_table.sql → Tabla de configuración
│           ├── V7__create_booking_requests_table.sql → Tabla de solicitudes
│           ├── V8__fix_schema_alignment.sql → Alineación de esquema
│           └── V9__create_sequences.sql → Secuencias para IDs
│
├── pom.xml
│   └── Configuración Maven, dependencias, plugins (Java 17, Lombok)
│
├── mvnw & mvnw.cmd
│   └── Maven Wrapper (ejecutar sin Maven instalado)
│
├── Procfile
│   └── Configuración para Heroku deployment
│
├── system.properties
│   └── Propiedades JVM (java.runtime.version=17)
│
└── README.md (Este archivo)
```

---

## ⚙️ Configuración y Runtime

### 1. **Compilación**

#### Requisitos Previos
- **Java 17+** (OpenJDK o Oracle)
- **Maven 3.8.1+** (incluido Maven Wrapper: `mvnw.cmd`)

#### Compilar Proyecto

```bash
# Windows CMD
mvnw.cmd clean install

# Linux/Mac
./mvnw clean install

# Si tienes Maven instalado globalmente
mvn clean install
```

**Opciones útiles:**
```bash
# Compilar sin ejecutar tests
mvnw.cmd clean install -DskipTests

# Build con profile específico
mvnw.cmd clean install -P local
mvnw.cmd clean install -P heroku

# Verbose (debugging)
mvnw.cmd clean install -X
```

### 2. **Ejecución Local**

#### Perfil Local (H2)

```bash
mvnw.cmd clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

O establecer variable de entorno:
```bash
set SPRING_PROFILES_ACTIVE=local
mvnw.cmd spring-boot:run
```

**La aplicación estará disponible en:**
- Portal de reservas: http://localhost:8080/
- Admin login: http://localhost:8080/login
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:file:./target/bunnycure-local`
  - Username: `sa`
  - Password: (vacío)

#### Credenciales por Defecto (Local)

| Usuario | Contraseña |
|---------|-----------|
| admin |  |

### 3. **Perfiles de Configuración (Profiles)**

#### Local (application-local.properties)
```ini
# H2 Database en archivo
spring.datasource.url=jdbc:h2:file:./target/bunnycure-local

# H2 Console habilitada
spring.h2.console.enabled=true

# Hibernate: DDL validate
spring.jpa.hibernate.ddl-auto=validate

# Flyway enabled (ejecuta migraciones)
spring.flyway.enabled=true

# Admin local
bunnycure.admin.username=admin
bunnycure.admin.password=
```

#### Heroku (application-heroku.properties)
```ini
# PostgreSQL
spring.datasource.url=${DATABASE_URL}
spring.jpa.hibernate.ddl-auto=validate

# Mail via Hostinger SMTP
spring.mail.host=${MAIL_HOST}
spring.mail.port=${MAIL_PORT}
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}

# Admin dinámico desde variables de entorno
bunnycure.admin.username=${ADMIN_USERNAME}
bunnycure.admin.password=${ADMIN_PASSWORD}
```

### 4. **Variables de Entorno**

#### Locales (desarrollo)

```bash
# Opcional en local (defaults a 8080)
set PORT=8080

# Flyway (opcional)
set FLYWAY_ENABLED=true
```

#### Producción (Heroku)

```bash
# Base de datos
DATABASE_URL=postgresql://user:password@host:5432/dbname
FLYWAY_ENABLED=true
FLYWAY_LOCATIONS=classpath:db/migration

# Mail
MAIL_HOST=smtp.hostinger.com
MAIL_PORT=587
MAIL_USERNAME=tu-email@bunnycure.cl
MAIL_PASSWORD=tu-password-smtp
MAIL_FROM=contacto@bunnycure.cl
MAIL_ENABLED=true

# WhatsApp (opcional)
WHATSAPP_NUMBER=56964499995

# Admin
ADMIN_USERNAME=admin
ADMIN_PASSWORD=tu-password-seguro

# Spring
SPRING_PROFILES_ACTIVE=heroku
```

### 5. **Ejecución de Tests**

```bash
# Ejecutar todos los tests
mvnw.cmd test

# Test específico
mvnw.cmd test -Dtest=AppointmentServiceTest

# Con cobertura (si está configurado)
mvnw.cmd test jacoco:report
```

---

## 🔌 Endpoints y Uso

### **Portales de Acceso**

La aplicación implementa **multi-tenancy por subdominio:**

| Subdominio | Propósito | Acceso |
|---|---|---|
| `reservar.bunnycure.cl` | Portal público de reservas | Anónimo |
| `admin.bunnycure.cl` | Área administrativa | Autenticado (ROLE_ADMIN) |
| `localhost` (dev) | Ambos portales | Según path |

### **Endpoints Públicos (Sin Autenticación)**

#### 1. Home / Redirección Inteligente
```http
GET /
```
**Respuesta:** Redirección condicional según subdominio/autenticación
- `admin.bunnycure.cl` + autenticado → `/dashboard`
- `admin.bunnycure.cl` + no autenticado → `/login`
- Cualquier otro → `/reservar`

#### 2. Portal de Reservas
```http
GET /reservar
```
**Respuesta:** HTML del formulario público de solicitud de reserva

**Estructura del formulario:**
```html
<form method="POST" action="/reservar/submit">
  <input name="fullName" required />        <!-- Nombre completo -->
  <input name="phone" pattern="\+56[0-9]{9}" required />  <!-- +56 + 9 dígitos -->
  <input name="email" type="email" />      <!-- Email opcional -->
  <select name="serviceId" required />      <!-- Servicio ofrecido -->
  <input name="preferredDate" type="date" required />  <!-- Fecha deseada -->
  <select name="preferredBlock">           <!-- Mañana / Tarde / Noche -->
    <option>Mañana</option>
    <option>Tarde</option>
    <option>Noche</option>
  </select>
  <textarea name="notes" />                <!-- Notas adicionales -->
  <button type="submit">Solicitar Reserva</button>
</form>
```

#### 3. Envío de Solicitud de Reserva
```http
POST /reservar/submit
Content-Type: application/x-www-form-urlencoded

fullName=Ana García
phone=%2B56964499995
email=ana@email.com
serviceId=1
preferredDate=2026-03-15
preferredBlock=Tarde
notes=Prefiero con exfoliación
```

**Respuestas:**
- **201 / 200 + Redirect:** Solicitud creada exitosamente
- **400:** Errores de validación (nombre, teléfono, etc.)
- **500:** Error interno del servidor

**Validaciones:**
- `fullName`: 2-100 caracteres (obligatorio)
- `phone`: Formato `+56` + 9 dígitos (obligatorio)
- `email`: Formato email válido (opcional)
- `serviceId`: Debe existir en BD (obligatorio)
- `preferredDate`: Fecha válida >= hoy (obligatorio)
- `preferredBlock`: Mañana/Tarde/Noche (obligatorio)
- `notes`: Máximo 500 caracteres

#### 4. Página de Login
```http
GET /login
```
**Respuesta:** Formulario HTML de autenticación

```html
<form method="POST" action="/login">
  <input name="username" required />
  <input name="password" type="password" required />
  <button type="submit">Ingresar</button>
</form>
```

#### 5. Envío de Credenciales
```http
POST /login
Content-Type: application/x-www-form-urlencoded

username=admin
password=
```

**Respuestas:**
- **302 Redirect:** `/dashboard` (éxito)
- **302 Redirect:** `/login?error` (fallo)

---

### **Endpoints Administrativos (Requieren ROLE_ADMIN)**

#### 6. Dashboard Principal
```http
GET /dashboard
```
**Respuesta:** HTML del panel administrativo con resumen estadístico

#### 7. Listar Solicitudes de Reserva
```http
GET /admin/booking-requests
```
**Respuesta JSON esperado (si fuera REST):**
```json
{
  "pending": [
    {
      "id": 1,
      "fullName": "Ana García",
      "phone": "+56964499995",
      "email": "ana@email.com",
      "service": "Manicura",
      "preferredDate": "2026-03-15",
      "preferredBlock": "Tarde",
      "notes": "Con exfoliación",
      "status": "PENDING",
      "createdAt": "2026-03-04T10:30:00"
    }
  ],
  "all": [...],
  "pendingCount": 1
}
```

#### 8. Detalle y Aprobar Solicitud
```http
GET /admin/booking-requests/{id}
```
**Respuesta:** Formulario HTML para aprobar/rechazar solicitud

```http
POST /admin/booking-requests/{id}/approve
Content-Type: application/x-www-form-urlencoded

appointmentDate=2026-03-15
appointmentTime=14%3A30
observations=Clienta confirmó disponibilidad
```

**Lógica:**
1. Valida datos de la cita
2. Crea `Appointment` con status CONFIRMED
3. Actualiza `BookingRequest` a APPROVED
4. Envía email de confirmación a la clienta (async)
5. Redirige a `/admin/booking-requests`

#### 9. Rechazar Solicitud
```http
POST /admin/booking-requests/{id}/reject
?reason=Servicio+no+disponible+en+esa+fecha
```

**Lógica:**
1. Actualiza `BookingRequest` a REJECTED
2. Envía email de rechazo a la clienta
3. Redirige a listado

#### 10. Listar Citas
```http
GET /admin/appointments
```
**Respuesta:** Listado HTML de todas las citas

#### 11. Detalle y Editar Cita
```http
GET /admin/appointments/{id}
```

```http
POST /admin/appointments/{id}
Content-Type: application/x-www-form-urlencoded

appointmentDate=2026-03-15
appointmentTime=14%3A30
customerId=1
serviceId=2
status=CONFIRMED
observations=Actualización
```

#### 12. Cancelar Cita
```http
POST /admin/appointments/{id}/cancel
```

**Lógica:**
1. Actualiza status a CANCELLED
2. Envía email de cancelación
3. Redirige a listado

#### 13. Recordatorios (página)
```http
GET /admin/appointments/reminders
```
**Respuesta:** Listado de citas confirmadas de mañana/próximos días

#### 14. Enviar Recordatorio Manual
```http
POST /admin/appointments/{id}/send-reminder
```

**Lógica:**
1. Envía email de recordatorio a la clienta
2. Actualiza `notificationSent = true`

#### 15. Gestionar Clientes
```http
GET /admin/customers
GET /admin/customers/{id}
POST /admin/customers (crear)
POST /admin/customers/{id} (actualizar)
```

#### 16. Gestionar Servicios
```http
GET /admin/services
GET /admin/services/{id}
POST /admin/services (crear)
POST /admin/services/{id} (actualizar)
```

#### 17. Gestionar Usuarios Admin
```http
GET /admin/users
POST /admin/users (crear nuevo admin)
POST /admin/users/{id}/change-password (cambiar contraseña)
```

#### 18. Configuración Global
```http
GET /admin/settings
POST /admin/settings (actualizar)
```

**Parámetros:**
```json
{
  "bookingEnabled": true,
  "reminderTime": "09:00",
  "mailEnabled": true
}
```

---

### **Endpoints Técnicos**

#### 19. Health Check (Actuator)
```http
GET /actuator/health
```
**Respuesta:**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "mail": {"status": "UP"}
  }
}
```

#### 20. Información de la Aplicación
```http
GET /actuator/info
```
**Respuesta:**
```json
{
  "app": {
    "name": "bunnycure",
    "version": "0.0.1-SNAPSHOT",
    "java": "17"
  }
}
```

#### 21. Well-Known Endpoints (SSL/HTTPS)
```http
GET /.well-known/acme-challenge/{token}
GET /.well-known/jwks.json
GET /.well-known/openid-configuration
```

---

## 📝 Ejemplos de Uso Completo

### **Caso 1: Cliente crea solicitud de reserva**

```bash
# 1. Cliente accede al portal
curl -i http://localhost:8080/reservar

# 2. Cliente envía formulario
curl -X POST http://localhost:8080/reservar/submit \
  -d "fullName=María López" \
  -d "phone=%2B56987654321" \
  -d "email=maria@email.com" \
  -d "serviceId=1" \
  -d "preferredDate=2026-03-20" \
  -d "preferredBlock=Tarde" \
  -d "notes=Primer manicura"

# 3. Sistema guarda solicitud y envía email de confirmación (async)
# Response: 302 Redirect a /reservar?success=true (o similar)

# 4. Admin notificado de nueva solicitud (en dashboard)
```

### **Caso 2: Admin aprueba solicitud y crea cita**

```bash
# 1. Admin loguea
curl -X POST http://localhost:8080/login \
  -d "username=admin" \
  -d "password=" \
  -c cookies.txt

# 2. Admin visualiza solicitud
curl -i -b cookies.txt http://localhost:8080/admin/booking-requests/1

# 3. Admin aprueba solicitud
curl -X POST -b cookies.txt http://localhost:8080/admin/booking-requests/1/approve \
  -d "appointmentDate=2026-03-20" \
  -d "appointmentTime=14:30" \
  -d "observations=Clienta confirmó"

# 4. Sistema crea Cita, marca solicitud como APPROVED
# 5. Envía email de confirmación a clienta
# Response: 302 Redirect a /admin/booking-requests
```

### **Caso 3: Sistema envía recordatorio automático**

```bash
# 1. Programador diario se ejecuta a las 09:00 AM (ReminderScheduler)
#    - Busca citas confirmadas para mañana
#    - Envía email de recordatorio (async)
#    - Marca notificationSent = true

# 2. Cliente recibe email: "Recordatorio: Tu cita en BunnyCure mañana a las 14:30"

# 3. Admin puede ver en /admin/appointments/reminders que fue enviado
```

---

## 🚀 Guía de Desarrollo

### **1. Agregar un Nuevo Servicio Estético**

**Pasos:**
1. En BD (o via admin UI): Crear registro en `service_catalog`
   ```sql
   INSERT INTO service_catalog (name, description, price, duration_minutes, active)
   VALUES ('Pedicura Francesa', 'Manicura de pies con diseño francés', 35000, 45, true);
   ```

2. El servicio aparecerá automáticamente en:
   - Dropdown de `/reservar`
   - Dropdown de aprobación de solicitudes
   - Formulario de citas

### **2. Agregar Validaciones Personalizadas**

**Ejemplo:** Validar que el teléfono no sea repetido

```java
// en BookingRequestDto
@NotBlank
@UniquePhoneNumber  // Custom validator
private String phone;

// Crear validator
@Component
public class UniquePhoneNumberValidator implements ConstraintValidator<UniquePhoneNumber, String> {
    @Autowired
    private BookingRequestRepository repo;
    
    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        return !repo.existsByPhone(phone);
    }
}
```

### **3. Agregar Notificación Personalizada**

**Ejemplo:** Enviar SMS cuando se confirma una cita

```java
// en NotificationService.java
@Async
public void sendSMSConfirmation(Appointment appointment) {
    String message = "Tu cita en BunnyCure está confirmada para " + 
                     appointment.getAppointmentDate() + " a las " + 
                     appointment.getAppointmentTime();
    
    // Integrar con Twilio / AWS SNS
    smsService.send(appointment.getCustomer().getPhone(), message);
}

// Llamar en AppointmentService
if (dto.getStatus() == AppointmentStatus.CONFIRMED) {
    notificationService.sendSMSConfirmation(appointment);
}
```

### **4. Crear Nueva Entidad JPA**

**Ejemplo:** Agregar tabla de "Reseñas"

```java
// src/main/java/cl/bunnycure/domain/model/Review.java
@Entity
@Table(name = "reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;
    
    @Column(nullable = false)
    private Integer rating;  // 1-5
    
    @Column(length = 500)
    private String comment;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
}
```

**Repository:**
```java
// src/main/java/cl/bunnycure/domain/repository/ReviewRepository.java
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByAppointment(Appointment appointment);
}
```

**Migración Flyway:**
```sql
-- src/main/resources/db/migration/V10__create_reviews_table.sql
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### **5. Agregar Endpoint REST API (JSON)**

**Ejemplo:** API JSON para citas

```java
// src/main/java/cl/bunnycure/web/controller/api/AppointmentApiController.java
@RestController
@RequestMapping("/api/appointments")
public class AppointmentApiController {
    
    @Autowired
    private AppointmentService appointmentService;
    
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentDto> getAppointment(@PathVariable Long id) {
        try {
            var appointment = appointmentService.findById(id);
            var dto = new AppointmentDto(/* mapper */);
            return ResponseEntity.ok(dto);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping
    public ResponseEntity<List<AppointmentDto>> listAppointments() {
        var appointments = appointmentService.findAll();
        var dtos = appointments.stream()
            .map(AppointmentDto::new)
            .toList();
        return ResponseEntity.ok(dtos);
    }
    
    @PostMapping
    public ResponseEntity<AppointmentDto> createAppointment(@Valid @RequestBody AppointmentDto dto) {
        var appointment = appointmentService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AppointmentDto(appointment));
    }
}
```

---

## 🔐 Variables de Entorno Detalladas

### **Base de Datos**

| Variable | Descripción | Ejemplo (Local) | Ejemplo (Heroku) |
|----------|-----------|---|---|
| `SPRING_DATASOURCE_URL` | URL conexión BD | `jdbc:h2:file:./target/bunnycure-local` | `postgresql://user:pass@host:5432/db` |
| `SPRING_DATASOURCE_USERNAME` | Usuario BD | `sa` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña BD | (vacío) | (env var) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | DDL strategy | `validate` | `validate` |
| `SPRING_FLYWAY_ENABLED` | Ejecutar migraciones | `true` | `true` |

### **Mail / SMTP**

| Variable | Descripción | Obligatorio |
|----------|-----------|---|
| `MAIL_HOST` | Host SMTP | Sí (si `MAIL_ENABLED=true`) |
| `MAIL_PORT` | Puerto SMTP | Sí |
| `MAIL_USERNAME` | Usuario SMTP | Sí |
| `MAIL_PASSWORD` | Contraseña SMTP | Sí |
| `MAIL_FROM` | Email remitente | Sí |
| `MAIL_ENABLED` | Habilitar envío de emails | No (default: true) |

**Ejemplo Hostinger:**
```bash
MAIL_HOST=smtp.hostinger.com
MAIL_PORT=587
MAIL_USERNAME=contacto@bunnycure.cl
MAIL_PASSWORD=tu-password-smtp-hostinger
MAIL_FROM=contacto@bunnycure.cl
```

### **Seguridad**

| Variable | Descripción | Default |
|----------|-----------|---|
| `BUNNYCURE_ADMIN_USERNAME` | Username admin | `admin` |
| `BUNNYCURE_ADMIN_PASSWORD` | Password admin | `` |

### **Integraciones**

| Variable | Descripción | Opcional |
|----------|-----------|---|
| `WHATSAPP_NUMBER` | Número WhatsApp negocio | Sí |

### **Spring / Runtime**

| Variable | Descripción |
|----------|-----------|
| `PORT` | Puerto HTTP | (default: 8080) |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | (local, heroku) |
| `JAVA_OPTS` | Opciones JVM | (heap, etc.) |

---

## 📦 Despliegue

### **Despliegue en Heroku**

#### 1. Preparación

```bash
# Instalar Heroku CLI
# https://devcenter.heroku.com/articles/heroku-cli

heroku login

# Crear aplicación
heroku create bunnycure-prod

# Agregar base de datos PostgreSQL
heroku addons:create heroku-postgresql:hobby-dev

# Verificar variable DATABASE_URL
heroku config:get DATABASE_URL
```

#### 2. Configurar Variables de Entorno

```bash
heroku config:set SPRING_PROFILES_ACTIVE=heroku
heroku config:set MAIL_HOST=smtp.hostinger.com
heroku config:set MAIL_PORT=587
heroku config:set MAIL_USERNAME=contacto@bunnycure.cl
heroku config:set MAIL_PASSWORD=tu-password
heroku config:set MAIL_FROM=contacto@bunnycure.cl
heroku config:set ADMIN_USERNAME=admin
heroku config:set ADMIN_PASSWORD=tu-password-seguro
heroku config:set WHATSAPP_NUMBER=56964499995
```

#### 3. Desplegar

```bash
# Desde rama main/master
git push heroku main

# O forzar push desde rama local
git push heroku tu-rama:main

# Ver logs
heroku logs --tail
```

#### 4. Ejecutar Migraciones (si fuera necesario)

```bash
heroku run "mvnw flyway:migrate" -a bunnycure-prod
```

#### 5. Configurar Dominio Personalizado

```bash
heroku domains:add bunnycure.cl
heroku domains:add admin.bunnycure.cl
heroku domains:add reservar.bunnycure.cl

# En tu registrador de dominios:
# CNAME bunnycure.cl → bunnycure-prod.herokuapp.com
# CNAME admin.bunnycure.cl → bunnycure-prod.herokuapp.com
# CNAME reservar.bunnycure.cl → bunnycure-prod.herokuapp.com
```

### **Despliegue en Docker (Opcional)**

**Crear Dockerfile:**
```dockerfile
FROM openjdk:17-slim
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

**Build:**
```bash
mvnw.cmd clean package
docker build -t bunnycure:latest .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=heroku \
  -e DATABASE_URL=... \
  -e MAIL_HOST=... \
  bunnycure:latest
```

---

## 🔧 Troubleshooting

### **Problema: "Failed to configure a DataSource"**

**Causa:** BD no configurada correctamente

**Solución:**
```bash
# En local, asegurar H2 está en classpath (maven dependency)
mvnw.cmd clean install

# Verificar application-local.properties existe
# spring.datasource.url=jdbc:h2:file:./target/bunnycure-local
```

### **Problema: "No suitable driver found"**

**Causa:** Driver JDBC faltante

**Solución:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
    <scope>runtime</scope>
</dependency>

<!-- O para PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

### **Problema: "Mail authentication failed"**

**Causa:** Credenciales SMTP inválidas

**Solución:**
```bash
# Verificar credenciales
heroku config:get MAIL_USERNAME
heroku config:get MAIL_PASSWORD

# Si es Hostinger, usar:
# MAIL_HOST=smtp.hostinger.com
# MAIL_PORT=587
# MAIL_SECURITY=TLS

# Desabilitar mail en desarrollo
set MAIL_ENABLED=false
```

### **Problema: "Flyway validation failed"**

**Causa:** Schema BD no coincide con migraciones

**Solución:**
```bash
# En local, limpiar BD
# Eliminar archivo target/bunnycure-local.mv.db
# Volver a ejecutar con Flyway habilitado

# En Heroku
heroku pg:reset DATABASE
heroku run "mvnw flyway:migrate"
```

---

## 📚 Recursos Adicionales

- **Spring Boot Docs:** https://spring.io/projects/spring-boot
- **Spring Data JPA:** https://spring.io/projects/spring-data-jpa
- **Spring Security:** https://spring.io/projects/spring-security
- **Thymeleaf:** https://www.thymeleaf.org/
- **Flyway:** https://flywaydb.org/
- **Heroku Deployment:** https://devcenter.heroku.com/

---

## 📄 Versionado y Changelog

### **v0.0.1-SNAPSHOT**

**Características:**
- ✅ Portal público de solicitud de reservas
- ✅ Panel administrativo
- ✅ Gestión de citas
- ✅ Notificaciones por email
- ✅ Recordatorios automáticos (cron jobs)
- ✅ Autenticación Spring Security
- ✅ H2 local + PostgreSQL producción
- ✅ Flyway migrations

**Por Implementar:**
- SMS notifications (Twilio)
- Integración calendario externo (Google Calendar)
- Reseñas/calificaciones
- Reportes analíticos
- Multi-idioma (i18n)
- 2FA (Two-Factor Authentication)

---

## 👨‍💼 Autor y Contacto

**Documentación Generada:** 4 de Marzo de 2026

Para soporte técnico o contribuciones, contactar al equipo de desarrollo en: `contacto@bunnycure.cl`

---

**⚠️ Este documento es confidencial y está destinado únicamente para fines de desarrollo y operación interna.**
