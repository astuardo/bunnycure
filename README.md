# 💅 BunnyCure – Sistema de Gestión de Citas de Manicure

> **Sistema integral de reserva y gestión de citas manicure** con autenticación segura, notificaciones automáticas por email y recordatorios programados. Arquitectura escalable basada en Spring Boot 3.2.11 con soporte para múltiples entornos (local H2 y producción PostgreSQL).

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
- Password: `changeme` (configurable: `bunnycure.admin.password`)
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

## 📁 Estructura de Carpetas (ver documento completo para detalles)

La estructura sigue el patrón de capas:
- `config/`: Configuración de seguridad, mail, async, scheduling
- `domain/`: Modelos JPA, enums, repositorios
- `service/`: Lógica de negocio y transacciones
- `web/`: Controllers y DTOs con validaciones
- `exception/`: Manejo centralizado de excepciones
- `resources/`: Properties, templates Thymeleaf, migraciones Flyway

---

## ⚙️ Configuración y Runtime

### 1. **Compilación**

```bash
# Windows CMD
mvnw.cmd clean install

# Linux/Mac
./mvnw clean install
```

### 2. **Ejecución Local (H2)**

```bash
set SPRING_PROFILES_ACTIVE=local
mvnw.cmd spring-boot:run
```

**URLs:**
- Portal: http://localhost:8080/reservar
- Admin: http://localhost:8080/login
- H2 Console: http://localhost:8080/h2-console

**Credenciales:**
- Username: `admin`
- Password: `changeme`

### 3. **Perfiles (Profiles)**

- **local**: H2 database, H2 console habilitada, Flyway activo
- **heroku**: PostgreSQL, Mail SMTP Hostinger, Admin dinámico

### 4. **Variables de Entorno**

**Producción (Heroku):**
```bash
DATABASE_URL=postgresql://...
MAIL_HOST=smtp.hostinger.com
MAIL_PORT=587
MAIL_USERNAME=contacto@bunnycure.cl
MAIL_PASSWORD=...
MAIL_FROM=contacto@bunnycure.cl
ADMIN_USERNAME=admin
ADMIN_PASSWORD=...
```

---

## 🔌 Endpoints Principales

### **Públicos (Sin Autenticación)**

| Endpoint | Método | Descripción |
|---|---|---|
| `/` | GET | Redirección inteligente por subdominio |
| `/reservar` | GET | Portal de solicitud de reserva |
| `/reservar/submit` | POST | Crear solicitud de reserva |
| `/login` | GET/POST | Página y procesamiento de login |

### **Administrativos (ROLE_ADMIN)**

| Endpoint | Método | Descripción |
|---|---|---|
| `/dashboard` | GET | Panel principal |
| `/admin/booking-requests` | GET | Listar solicitudes |
| `/admin/booking-requests/{id}/approve` | POST | Aprobar y crear cita |
| `/admin/booking-requests/{id}/reject` | POST | Rechazar solicitud |
| `/admin/appointments` | GET | Listar citas |
| `/admin/appointments/reminders` | GET | Recordatorios pendientes |
| `/admin/customers` | GET | Gestión de clientes |
| `/admin/services` | GET | Gestión de servicios |
| `/admin/settings` | GET | Configuración global |
| `/actuator/health` | GET | Health check |

---

## 🚀 Despliegue (Heroku)

```bash
heroku create bunnycure-prod
heroku addons:create heroku-postgresql:hobby-dev
heroku config:set SPRING_PROFILES_ACTIVE=heroku
heroku config:set MAIL_HOST=smtp.hostinger.com
# ... más variables
git push heroku main
```

---

## 📚 Documentación Completa

Para una documentación **exhaustiva** incluyendo:
- Descripción detallada de todas las entidades JPA
- Flujos completos de procesos (reserva, aprobación, recordatorios)
- Ejemplos de uso con CURL
- Guía extendida de desarrollo
- Troubleshooting avanzado
- Diagramas de arquitectura y secuencia

👉 **Ver archivo `README_TECNICO.md`**

---

## 📄 Información de Versión

- **Version:** 0.0.1-SNAPSHOT
- **Java:** 17 LTS
- **Spring Boot:** 3.2.11
- **Generated:** 4 de Marzo de 2026

---

**Para soporte:** contacto@bunnycure.cl
