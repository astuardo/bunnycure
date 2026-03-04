# Sistema de Recordatorios de Citas - Bunny Cure

## Descripción General
Se ha implementado un sistema completo de recordatorios automáticos para las citas agendadas. El sistema envía recordatorios por email y WhatsApp a los clientes para recordarles sus citas próximas.

## Características Implementadas

### 1. **Recordatorios Automáticos Programados**
   - **9:00 AM diarios**: Envía recordatorios de citas programadas para mañana
   - **Cada 2 horas**: Envía recordatorios de citas próximas (en 2 horas)
   - Los recordatorios se envían automáticamente sin necesidad de intervención manual

### 2. **Métodos de Notificación**
   - **Email**: Plantillas HTML profesionales personalizadas para cada tipo de recordatorio
   - **WhatsApp**: Integración lista para servicios como Twilio o MessageBird

### 3. **Plantillas de Email**
   - **reminder-tomorrow.html**: Para recordatorios de citas del día siguiente
   - **reminder-2hours.html**: Para recordatorios urgentes de citas en 2 horas

### 4. **Interfaz de Administración**
   - Página `/admin/appointments/reminders` para gestionar recordatorios manualmente
   - Listado de citas pendientes de recordatorio
   - Botones para enviar recordatorios de forma individual o en lote
   - Información sobre los recordatorios automáticos

## Componentes Técnicos

### Clases Principales
1. **ReminderScheduler** (`config/ReminderScheduler.java`)
   - Componente Spring que ejecuta tareas programadas
   - Utiliza `@Scheduled` para ejecutar recordatorios automáticos

2. **AdminAppointmentController** (`web/controller/AdminAppointmentController.java`)
   - Controlador para gestionar recordatorios manualmente
   - Endpoints:
     - `GET /admin/appointments/reminders`: Muestra lista de recordatorios
     - `POST /admin/appointments/{id}/send-reminder`: Envía recordatorio individual
     - `POST /admin/appointments/send-all-reminders`: Envía todos los recordatorios

3. **AppointmentService** (actualizaciones)
   - `sendRemindersForUpcomingAppointments()`: Envía recordatorios para citas de mañana
   - `sendRemindersForAppointmentsIn2Hours()`: Envía recordatorios para citas próximas
   - `findByStatus()`: Busca citas por estado

4. **NotificationService** (actualizaciones)
   - `sendReminderNotification()`: Envía recordatorios por email y WhatsApp
   - `sendWhatsAppMessage()`: Placeholder para integración de WhatsApp

### Modelo de Datos
- Se agregó el campo `reminder_sent` a la tabla `appointments`
- Se creó un índice para optimizar búsquedas de citas pendientes

### Base de Datos
- Migración: `V3__add_reminder_sent.sql`
  - Agrega columna `reminder_sent` a la tabla appointments
  - Crea índice para mejor rendimiento

## Cómo Usar

### Recordatorios Automáticos
Los recordatorios se envían automáticamente según el cronograma programado. No se requiere acción del usuario.

### Recordatorios Manuales
1. Navega a `/admin/appointments/reminders`
2. Verás un listado de citas pendientes
3. Puedes enviar:
   - Recordatorios individuales usando el botón de cada cita
   - Recordatorios en lote usando los botones superiores

## Configuración

### Expresiones Cron (en ReminderScheduler.java)
```java
// 9:00 AM diarios
@Scheduled(cron = "0 0 9 * * *")

// Cada 2 horas
@Scheduled(cron = "0 0 */2 * * *")
```

Puedes ajustar estos horarios modificando las expresiones cron según tus necesidades.

## Integración de WhatsApp

Actualmente, el envío de WhatsApp está configurado como un placeholder. Para habilitar el envío real:

1. Registrate en un servicio como Twilio o MessageBird
2. Reemplaza el método `sendWhatsAppMessage()` con la integración real
3. Añade las credenciales en `application.properties`

Ejemplo con Twilio:
```java
private void sendWhatsAppMessage(String phoneNumber, String message) {
    // Usar Twilio SDK para enviar el mensaje
}
```

## Testing

### Pruebas Manuales
1. Crea una cita para mañana
2. Navega a `/admin/appointments/reminders`
3. Verifica que la cita aparezca en la lista
4. Haz clic en "Enviar recordatorio"
5. Verifica que el email fue recibido

### Pruebas de Recordatorios Automáticos
1. Los recordatorios se ejecutan automáticamente según el cronograma
2. Revisa los logs para confirmar: `[INFO] ... recordatorios ... enviados exitosamente`

## Logs y Monitoreo

El sistema registra:
- Envío exitoso: `[MAIL-OK] <subject> → <email>`
- Errores de email: `[MAIL-ERROR] <error>`
- Avisos de WhatsApp: `[WHATSAPP-WARN] <warning>`
- Recordatorios enviados: `[INFO] Recordatorios ... enviados exitosamente`

## Próximas Mejoras

1. Panel de estadísticas de recordatorios enviados
2. Historial de recordatorios por cliente
3. Notificaciones SMS adicionales
4. Configuración personalizable de horarios de recordatorios
5. Reintento automático de recordatorios fallidos

---

**Última actualización**: 4 de Marzo de 2026
