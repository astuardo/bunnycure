# 🚀 INICIO RÁPIDO - Sistema de Recordatorios

## ¿Qué se implementó?
Un sistema **automático** que envía recordatorios de citas por email y WhatsApp.

## 📅 ¿Cuándo se ejecuta?
- **Automáticamente**: Todos los días a las **08:00 AM** (hora Santiago)
- **Manualmente**: Desde el panel de admin `/admin/reminders` en cualquier momento

## 🎯 ¿Qué hace?
1. Busca todas las citas **pendientes para HOY**
2. Obtiene datos del cliente y la cita
3. Envía **email personalizado** con detalles
4. Envía **WhatsApp** con confirmación
5. Marca la cita como "recordatorio enviado" (previene duplicados)

## 📝 ¿Qué reciben los clientes?

### Email
```
🐰 Recordatorio de tu cita - Bunny Cure

¡Hola María! 👋
Te recordamos que tienes una cita HOY

🎀 Servicio: Manicure + Brillo
📅 Fecha: 4 de marzo de 2026
🕐 Hora: 14:30

⏰ Por favor llega 5 minutos antes
```

### WhatsApp
```
🐰 Recordatorio de cita - Bunny Cure
¡Hola María! 👋
Te recordamos que tienes una cita HOY 📅
🎀 Servicio: Manicure + Brillo
🕐 Hora: 14:30
⏰ Por favor llega 5 minutos antes
```

## 🎛️ Panel de Administración
**URL**: `https://admin.bunnycure.cl/admin/reminders`

**Qué puedes hacer:**
- ✅ Ver lista de citas pendientes de recordatorio para hoy
- ✅ Enviar recordatorio a una cita específica
- ✅ Enviar recordatorios a todas las citas de hoy
- ✅ Ver estadísticas en tiempo real

## ⚙️ Configuración Mínima

Agregar a `application-local.properties`:

```properties
# Email
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=tu-email@gmail.com
spring.mail.password=tu-contraseña-app
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
bunnycure.mail.enabled=true
bunnycure.mail.from=noreply@bunnycure.cl

# WhatsApp (opcional)
bunnycure.whatsapp.number=56964499995
bunnycure.whatsapp.enabled=true
```

## 🔍 Cómo Verificar que Funciona

**En los logs** (buscar `[REMINDER]`):
```
[REMINDER] Iniciando envío de recordatorios diarios...
[REMINDER] Se encontraron 5 citas pendientes para recordar
[REMINDER] ✅ Recordatorio enviado para cita ID: 123
[REMINDER] Envío de recordatorios completado
```

**En la base de datos:**
- Campo `appointments.notificationSent`
- Si es `true` = recordatorio ya enviado
- Si es `false` = elegible para recordatorio

## 🐛 Si No Funciona

1. **¿Se ejecuta el scheduler?**
   - Verificar `@EnableScheduling` en BunnycureApplication.java ✅

2. **¿Se envían emails?**
   - Verificar credenciales SMTP en application-local.properties
   - Verificar que `bunnycure.mail.enabled=true`
   - Buscar logs con `[MAIL]`

3. **¿Se envían duplicados?**
   - Verificar que `notificationSent` se actualice en BD
   - No ejecutar manualmente si scheduler ya envió

## 📱 API para Integración Externa

```bash
# Enviar recordatorio para una cita específica
curl -X POST http://localhost:8080/admin/reminders/send/123

# Enviar recordatorios para todas las citas de hoy
curl -X POST http://localhost:8080/admin/reminders/send-today

# Obtener estadísticas
curl -X GET http://localhost:8080/admin/reminders/stats
```

## 📊 Clases Principales

| Clase | Función |
|-------|---------|
| `AppointmentReminderService` | Lógica principal de recordatorios |
| `NotificationService` | Envío de emails |
| `AdminRemindersController` | API y panel de admin |
| `SchedulingConfig` | Configuración del scheduler |

## 🎓 Documentación Completa

Ver archivos:
- `RECORDATORIOS.md` - Documentación técnica detallada
- `IMPLEMENTACION_RECORDATORIOS.md` - Guía de características
- `RESUMEN_RECORDATORIOS.txt` - Este archivo pero más visual

## ✨ Resumen

| Aspecto | Detalles |
|--------|----------|
| **Ejecución** | Automática diaria a las 08:00 AM + Manual desde admin |
| **Canales** | Email (HTML profesional) + WhatsApp |
| **Seguridad** | Solo ROLE_ADMIN |
| **Base de Datos** | Campo `notificationSent` previene duplicados |
| **Logs** | Patrón `[REMINDER]` para debugging |
| **Panel Admin** | `/admin/reminders` con interfaz completa |
| **Estado** | ✅ Completado y listo |

---

**¿Necesitas ayuda?** Revisa los archivos de documentación o busca logs con `[REMINDER]`
