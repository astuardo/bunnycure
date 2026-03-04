# 🐰 Sistema de Recordatorios de Citas - Bunny Cure
## Guía de Implementación y Uso

---

## ✅ Lo que se ha implementado

### 1. Sistema de Recordatorios Automáticos
El sistema envía recordatorios automáticamente en dos momentos:
- **Cada mañana a las 9:00 AM**: Recordatorios de citas programadas para mañana
- **Cada 2 horas**: Recordatorios de citas que ocurren en las próximas 2 horas

### 2. Canales de Notificación
- **Email**: Con plantillas HTML profesionales y personalizadas
- **WhatsApp**: Integración lista (placeholder listo para Twilio/MessageBird)

### 3. Interfaz de Administración
- URL: `http://localhost:8080/admin/appointments/reminders`
- Permite:
  - Ver citas pendientes de recordatorio
  - Enviar recordatorios individuales
  - Enviar recordatorios en lote (para mañana o 2 horas)
  - Información sobre recordatorios automáticos

---

## 📝 Archivos Creados/Modificados

### Nuevos Archivos
```
src/main/java/cl/bunnycure/config/ReminderScheduler.java
src/main/java/cl/bunnycure/web/controller/AdminAppointmentController.java
src/main/resources/templates/mail/reminder-tomorrow.html
src/main/resources/templates/mail/reminder-2hours.html
src/main/resources/templates/admin/appointments/reminders.html
src/main/resources/db/migration/V3__add_reminder_sent.sql
```

### Archivos Modificados
```
src/main/java/cl/bunnycure/service/AppointmentService.java
src/main/java/cl/bunnycure/service/NotificationService.java
src/main/java/cl/bunnycure/domain/model/Appointment.java
src/main/java/cl/bunnycure/domain/repository/AppointmentRepository.java
```

---

## 🚀 Cómo Compilar y Ejecutar

### 1. Compilar el Proyecto
```bash
mvn clean compile
```

### 2. Ejecutar Migraciones de Base de Datos
Las migraciones se ejecutan automáticamente al iniciar la aplicación:
```bash
mvn spring-boot:run
```

### 3. Acceder a la Interfaz
- URL: `http://localhost:8080/admin/appointments/reminders`
- Requiere autenticación como administrador

---

## 📋 Estructura de la Base de Datos

### Nueva Columna en `appointments`
```sql
ALTER TABLE appointments
ADD COLUMN reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;

CREATE INDEX idx_appointments_reminder_sent 
ON appointments(reminder_sent, appointment_date, status);
```

---

## 🔧 Configuración

### Cambiar Horarios de Recordatorios
Edita `ReminderScheduler.java`:

```java
// Cambiar hora de envío de recordatorios diarios
@Scheduled(cron = "0 0 9 * * *")  // Actualmente: 9:00 AM
public void sendDailyReminders() { ... }

// Cambiar frecuencia de recordatorios de 2 horas
@Scheduled(cron = "0 0 */2 * * *")  // Actualmente: cada 2 horas
public void sendTwoHourReminders() { ... }
```

### Expresiones Cron Comunes
- `0 0 9 * * *` → 9:00 AM diarios
- `0 0 */4 * * *` → Cada 4 horas
- `0 30 10 * * *` → 10:30 AM diarios
- `0 0 0 * * *` → Medianoche

---

## 📧 Plantillas de Email

### reminder-tomorrow.html
- Se envía a citas programadas para el día siguiente
- Diseño amigable con información de la cita
- Incluye emoji para mayor atractivo visual

### reminder-2hours.html
- Se envía a citas que ocurren en 2 horas
- Diseño más urgente (color rojo/naranja)
- Enfatiza la cercanía de la cita

---

## 🔐 Seguridad y Validación

### Control de Acceso
- Solo usuarios con rol ADMIN pueden acceder a `/admin/appointments/reminders`
- Los recordatorios solo se envían a citas con estado CONFIRMED
- Se rastrea qué recordatorios fueron enviados con el campo `reminder_sent`

### Manejo de Errores
- Los errores en un recordatorio no detienen el procesamiento de otros
- Los errores se registran en los logs
- Se puede reintentar el envío manualmente desde la interfaz

---

## 📊 Monitoreo y Logs

### Líneas de Log a Buscar
```
[INFO] Iniciando envío de recordatorios diarios...
[MAIL-OK] 🐰 Tu cita es mañana - Bunny Cure → email@example.com
[MAIL-ERROR] No se pudo enviar a email@example.com: <error>
[INFO] Recordatorios diarios enviados exitosamente
```

---

## 🧪 Pruebas

### Prueba Manual de Recordatorios
1. Crea una cita para mañana (fecha futura)
2. Ve a `/admin/appointments/reminders`
3. Verifica que aparezca en el listado
4. Haz clic en "Enviar recordatorio"
5. Verifica el email recibido

### Prueba de Recordatorios Automáticos
1. Espera a las 9:00 AM o 2 horas
2. Revisa los logs de la aplicación
3. Busca líneas que digan "recordatorios enviados exitosamente"

---

## 🔌 Integración de WhatsApp

### Requisitos
1. Cuenta en Twilio, MessageBird, o similar
2. API credentials

### Pasos de Integración
1. Añade la dependencia en `pom.xml`:
```xml
<dependency>
    <groupId>com.twilio.sdk</groupId>
    <artifactId>twilio</artifactId>
    <version>9.0.0</version>
</dependency>
```

2. Añade configuración en `application.properties`:
```properties
whatsapp.account-sid=your-account-sid
whatsapp.auth-token=your-auth-token
whatsapp.phone-number=+1234567890
```

3. Reemplaza el método en `NotificationService.java`:
```java
private void sendWhatsAppMessage(String phoneNumber, String message) {
    Twilio.init(accountSid, authToken);
    Message.creator(new PhoneNumber(phoneNumber), // To number
                    new PhoneNumber(whatsappPhoneNumber), // From number
                    message)
        .create();
}
```

---

## 📱 Ejemplo de Flujo

### Escenario: Cita programada para mañana

1. **Cliente reserva cita** → 14:30 (2:30 PM)
2. **9:00 AM del día anterior** → Sistema ejecuta recordatorio diario
3. **Email enviado** → Cliente recibe "¡Tu cita es mañana!"
4. **Opcional**: Cliente recibe WhatsApp si está configurado
5. **Día de la cita - 12:30 PM** → Sistema ejecuta recordatorio de 2 horas
6. **Email enviado** → Cliente recibe "¡Tu cita es en 2 horas!"
7. **14:30** → Cliente llega a su cita

---

## 🎯 Próximas Mejoras Recomendadas

1. **Dashboard de Estadísticas**
   - Recordatorios enviados por día
   - Tasa de respuesta de clientes
   - Citas confirmadas vs. canceladas

2. **Configuración Flexible**
   - Permitir que cada cliente elija canales de notificación
   - Horarios personalizados de recordatorios
   - Idiomas de plantillas

3. **Reintento Automático**
   - Reintentar recordatorios fallidos
   - Cola de tareas para mejor rendimiento

4. **SMS Adicional**
   - Integración con servicios de SMS
   - Alternativa a WhatsApp

5. **Análisis**
   - Tasa de apertura de emails
   - Click-through rates
   - Abandono de citas

---

## ❓ Preguntas Frecuentes

**P: ¿Cómo cambio el horario de los recordatorios?**
R: Edita las expresiones cron en `ReminderScheduler.java`

**P: ¿Por qué no recibí un recordatorio?**
R: Verifica que la cita esté con estado CONFIRMED y que el email sea válido

**P: ¿Cómo envío recordatorios por WhatsApp?**
R: Sigue los pasos de integración arriba, necesitas una cuenta de Twilio/MessageBird

**P: ¿Puedo enviar recordatorios manualmente?**
R: Sí, ve a `/admin/appointments/reminders` y usa los botones de acción

**P: ¿Dónde veo los logs?**
R: En la consola de Spring Boot o en `logs/` si está configurado

---

## 📞 Soporte

Para reportar problemas o sugerencias, revisa:
- Los logs en la consola
- El archivo `REMINDERS_IMPLEMENTATION.md` para detalles técnicos
- El código comentado en cada clase

---

**Sistema implementado el 4 de Marzo de 2026**
**Para: Bunny Cure 🐰**
