# Sistema de Recordatorios de Citas - Bunny Cure

## 📋 Descripción General

El sistema de recordatorios automáticos envía notificaciones a los clientes sobre sus citas pendientes. Los recordatorios se pueden enviar por:
- 📧 **Email**
- 💬 **WhatsApp**

## ⚙️ Configuración

### Ejecución Automática
- **Hora**: 08:00 AM (hora local: America/Santiago)
- **Frecuencia**: Diariamente
- **Recordatorios**: Se envían para citas pendientes de **hoy**
- **Prevención de duplicados**: Solo se envían a citas que aún no han recibido recordatorio

### Configuración en `application.properties`
```properties
# Email
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=tu-email@gmail.com
spring.mail.password=tu-contraseña
bunnycure.mail.enabled=true
bunnycure.mail.from=noreply@bunnycure.cl

# WhatsApp
bunnycure.whatsapp.number=56964499995
bunnycure.whatsapp.enabled=true
```

## 🎯 Características

### 1. **Recordatorios Automáticos**
- Se ejecutan automáticamente cada día a las 8:00 AM
- Buscan todas las citas pendientes para ese día
- Envían notificaciones por email y WhatsApp
- Marcan las citas como "recordatorio enviado" para evitar duplicados

### 2. **Recordatorios Manuales**
- Panel de administración en `/admin/reminders`
- Posibilidad de enviar recordatorios individuales o en lote
- Visualización de citas pendientes de recordatorio
- Estadísticas en tiempo real

### 3. **Plantillas Personalizadas**
- **Email**: Plantilla HTML profesional en `templates/mail/reminder.html`
- **WhatsApp**: Mensaje personalizado con datos de la cita
- Incluyen datos: nombre del cliente, servicio, hora y fecha

## 📱 Panel de Administración

### Acceso
- **URL**: `/admin/reminders`
- **Permisos**: Solo administradores (`ROLE_ADMIN`)

### Funcionalidades
1. **Ver citas pendientes**: Lista todas las citas sin recordatorio para hoy
2. **Enviar recordatorios en lote**: Botón "Enviar Recordatorios Hoy"
3. **Enviar recordatorio individual**: Botón por cada cita
4. **Ver estadísticas**: Contador de citas pendientes

## 🔧 Clases Principales

### `AppointmentReminderService`
Servicio principal que gestiona los recordatorios.

**Métodos principales:**
```java
// Envío automático diario (cron)
@Scheduled(cron = "0 0 8 * * *", zone = "America/Santiago")
void sendDailyReminders()

// Envío manual de recordatorio individual
void sendManualReminder(Long appointmentId)

// Envío de recordatorio para una cita específica
void sendReminderForAppointment(Appointment appointment)
```

### `AppointmentRepository`
Incluye método para buscar citas pendientes de recordatorio:
```java
@Query("""
    SELECT a FROM Appointment a
    JOIN FETCH a.customer
    JOIN FETCH a.service
    WHERE a.status = :status
    AND a.notificationSent = false
    AND a.appointmentDate = :date
    ORDER BY a.appointmentTime ASC
""")
List<Appointment> findPendingRemindersForDate(
        @Param("status") AppointmentStatus status,
        @Param("date") LocalDate date
);
```

### `AdminRemindersController`
Controlador de administración de recordatorios.

**Endpoints:**
- `GET /admin/reminders` - Panel principal
- `POST /admin/reminders/send-today` - Enviar recordatorios de hoy
- `POST /admin/reminders/send/{appointmentId}` - Enviar recordatorio individual
- `GET /admin/reminders/stats` - Estadísticas (JSON)

## 📊 Flujo de Funcionamiento

```
┌─────────────────────────────────────┐
│   Cada día a las 08:00 AM           │
│   (Scheduler)                       │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│ sendDailyReminders()                │
│ - Busca citas pendientes para hoy   │
│ - Filtra no notificadas             │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│ sendReminderForAppointment()        │
│ Para cada cita:                     │
│  - Obtiene datos del cliente        │
│  - Obtiene datos de la cita         │
└────────────┬────────────────────────┘
             │
        ┌────┴────┐
        │          │
        ▼          ▼
   ┌────────┐  ┌──────────┐
   │ Email  │  │ WhatsApp │
   └────────┘  └──────────┘
        │          │
        └────┬─────┘
             │
             ▼
   ┌─────────────────────────┐
   │ Marca: notificationSent │
   │ = true                  │
   └─────────────────────────┘
```

## 📝 Ejemplo de Recordatorio por Email

**Asunto:** 🐰 Recordatorio de tu cita - Bunny Cure

**Contenido:**
```
¡Hola María! 👋

Te recordamos que tienes una cita programada HOY en Bunny Cure.

🎀 Servicio: Manicure + Brillo
📅 Fecha: 2026-03-04
🕐 Hora: 14:30

⏰ Por favor llega 5 minutos antes de la hora programada.

¿Necesitas ayuda o quieres reprogramar?
💬 Contactar por WhatsApp
```

## 📞 Ejemplo de Recordatorio por WhatsApp

```
🐰 *Recordatorio de cita - Bunny Cure*

¡Hola María! 👋

Te recordamos que tienes una cita *hoy* 📅

*Detalles:*
🎀 Servicio: Manicure + Brillo
🕐 Hora: 14:30

⏰ Por favor llega 5 minutos antes.

Si necesitas reprogramar o tienes dudas, contáctanos 💬
```

## 🚀 Usando el Sistema

### Envío Automático
No requiere acción del usuario. El sistema automáticamente:
1. Se ejecuta diariamente a las 08:00 AM
2. Busca citas pendientes para ese día
3. Envía recordatorios
4. Registra que se enviaron

### Envío Manual

#### Por Panel de Administración
1. Ir a `/admin/reminders`
2. Ver lista de citas pendientes
3. Hacer clic en "Enviar recordatorio" para una cita específica
4. O hacer clic en "Enviar Recordatorios Hoy" para todas

#### Por API (programáticamente)
```bash
# Enviar recordatorio para cita específica
POST /admin/reminders/send/{appointmentId}

# Response
{
  "success": true,
  "message": "Recordatorio enviado exitosamente ✅"
}
```

## ⚠️ Consideraciones Importantes

1. **Zona horaria**: El scheduler usa `America/Santiago`
2. **Email habilitado**: Requiere configuración SMTP
3. **Prevención de duplicados**: El campo `notificationSent` evita recordatorios duplicados
4. **Manejo de errores**: Los errores se registran en logs pero no interrumpen el proceso
5. **Async**: Los envíos de email son asincronicos para no bloquear la aplicación

## 🔍 Monitoreo

### Logs
Buscar logs con patrón `[REMINDER]`:
```
[REMINDER] Iniciando envío de recordatorios diarios...
[REMINDER] Se encontraron 5 citas pendientes para recordar
[REMINDER] ✅ Recordatorio enviado para cita ID: 123
[REMINDER] Envío de recordatorios completado
```

### Base de Datos
- Campo `notificationSent` en tabla `appointments` indica si se envió recordatorio
- Campo `updated_at` registra cuándo se actualizó

## 🐛 Troubleshooting

### No se envían recordatorios automáticos
- Verificar que `@EnableScheduling` esté en la clase principal
- Verificar configuración SMTP en `application.properties`
- Revisar logs con patrón `[REMINDER]`

### Errores en envío de email
- Verificar credenciales SMTP
- Verificar que la plantilla `mail/reminder.html` exista
- Revisar logs con patrón `[MAIL-ERROR]`

### Se envían recordatorios duplicados
- Verificar que `notificationSent` se esté actualizando en BD
- Revisar que no haya dos instancias de la aplicación

## 📚 Archivos Relacionados

```
src/main/java/
├── cl/bunnycure/
│   ├── service/
│   │   ├── AppointmentReminderService.java (⭐ Principal)
│   │   └── NotificationService.java (Envío de emails)
│   ├── web/controller/
│   │   └── AdminRemindersController.java (API Admin)
│   └── config/
│       └── SchedulingConfig.java (Configuración)

src/main/resources/
├── templates/
│   ├── mail/
│   │   └── reminder.html (Plantilla email)
│   └── admin/reminders/
│       └── index.html (Panel admin)
```

---

**Última actualización:** 2026-03-04
**Versión:** 1.0
