# 🐰 Guía de Pruebas Locales - Sistema de Recordatorios

## ✅ Checklist Pre-Compilación

Verifica que todos estos archivos existan:

### Backend Java
- ✅ `src/main/java/cl/bunnycure/config/ReminderScheduler.java`
- ✅ `src/main/java/cl/bunnycure/web/controller/AdminAppointmentController.java`

### Plantillas de Email
- ✅ `src/main/resources/templates/mail/reminder-tomorrow.html`
- ✅ `src/main/resources/templates/mail/reminder-2hours.html`

### Interfaz de Admin
- ✅ `src/main/resources/templates/admin/appointments/reminders.html`

### Migración de BD
- ✅ `src/main/resources/db/migration/V3__add_reminder_sent.sql`

### Configuración
- ✅ `src/main/resources/application.properties`
- ✅ `src/main/resources/application-local.properties`

---

## 🚀 Pasos para Probar en Local

### 1️⃣ Compilar el Proyecto

```bash
cd c:\Users\alfre\IdeaProjects\bunnycure
mvnw.cmd clean compile
```

**Resultado esperado:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXXs
```

### 2️⃣ Ejecutar en Modo Local

```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

O desde IntelliJ:
1. Click derecho en `BunnycureApplication.java`
2. Run → Edit Configurations
3. Environment variables: `SPRING_PROFILES_ACTIVE=local`
4. Click Run

### 3️⃣ Acceder a la Aplicación

- **URL Principal:** http://localhost:8080
- **H2 Console:** http://localhost:8080/h2-console
- **Admin Recordatorios:** http://localhost:8080/admin/appointments/reminders

### 4️⃣ Credenciales de Prueba

```
Usuario: admin
Contraseña: changeme
```

---

## 📧 Configuración de Email Locales

### Opción A: Mailhog (Recomendado para desarrollo)

1. **Descargar Mailhog:**
   https://github.com/mailhog/MailHog/releases

2. **Ejecutar Mailhog:**
   ```bash
   MailHog.exe
   ```

3. **URLs disponibles:**
   - SMTP: http://localhost:1025
   - Web UI: http://localhost:8025

4. **Los emails aparecerán en:** http://localhost:8025

### Opción B: MailDev

```bash
npm install -g maildev
maildev
```

- SMTP: localhost:1025
- Web UI: http://localhost:1080

### Opción C: Usar Hostinger (Real)

En `application-local.properties`, descomenta:

```properties
spring.mail.host=smtp.hostinger.com
spring.mail.port=587
spring.mail.username=tu-email@bunnycure.cl
spring.mail.password=tu-contraseña
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
```

---

## 🧪 Pruebas Funcionales

### Test 1: Verificar Base de Datos

1. Abre http://localhost:8080/h2-console
2. Verifica que la tabla `appointments` tiene el campo `reminder_sent`

```sql
SELECT * FROM appointments;
```

### Test 2: Crear una Cita de Prueba

1. Inicia sesión como admin
2. Crea una cita para **mañana** a las **10:00 AM**
3. Confirma la cita

### Test 3: Enviar Recordatorio Manual

1. Ve a http://localhost:8080/admin/appointments/reminders
2. Deberías ver la cita creada
3. Haz click en "Enviar Ahora"
4. Verifica el email en Mailhog (http://localhost:8025)

### Test 4: Botones de Acciones Rápidas

1. En la página de recordatorios, prueba:
   - "Enviar Recordatorios para Mañana"
   - "Enviar Recordatorios para Próximas 2 Horas"
2. Verifica los emails en Mailhog

### Test 5: Recordatorios Automáticos

1. Los recordatorios se ejecutarán automáticamente a:
   - **9:00 AM** → Citas para mañana
   - **Cada 2 horas** → Citas en próximas 2 horas

2. En `application-local.properties`, puedes cambiar los horarios para pruebas rápidas:
   ```properties
   # Ejecutar cada minuto para pruebas
   schedule.reminder.daily=0 * * * * *
   schedule.reminder.urgent=0 * * * * *
   ```

---

## 📋 Verificación de Logs

En la consola, busca estos mensajes:

```
[REMINDER-SCHEDULER] Iniciando envío de recordatorios diarios...
[REMINDER-SCHEDULER] Recordatorios diarios enviados exitosamente
[MAIL] Email enviado a: cliente@example.com
[ADMIN] Mostrando página de recordatorios
```

---

## 🛠️ Solución de Problemas

### Error: "No se encuentra la clase ReminderScheduler"
**Solución:** Verifica que `ReminderScheduler.java` esté en `src/main/java/cl/bunnycure/config/`

### Error: "Table 'appointments' has no column 'reminder_sent'"
**Solución:** Ejecuta la migración manualmente:
```sql
ALTER TABLE appointments ADD COLUMN reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;
```

### Los emails no se envían
**Solución:** 
1. Verifica que Mailhog esté ejecutándose
2. Comprueba los logs de la aplicación
3. Verifica el puerto SMTP (1025 por defecto)

### Error de permisos en Windows
**Solución:** 
```bash
mvnw.cmd clean compile
```
(Asegúrate de ejecutar en una terminal con permisos de administrador)

---

## 📦 Archivos Generados

```
src/main/java/cl/bunnycure/
├── config/
│   └── ReminderScheduler.java          ✨ NUEVO
├── web/controller/
│   └── AdminAppointmentController.java ✨ NUEVO

src/main/resources/
├── templates/
│   ├── mail/
│   │   ├── reminder-tomorrow.html      ✨ NUEVO
│   │   └── reminder-2hours.html        ✨ NUEVO
│   └── admin/appointments/
│       └── reminders.html              ✨ NUEVO
├── db/migration/
│   └── V3__add_reminder_sent.sql       ✨ NUEVO
├── application.properties               ✏️ ACTUALIZADO
└── application-local.properties         ✏️ ACTUALIZADO
```

---

## 🎯 Próximos Pasos

Después de compilar exitosamente:

1. **Prueba en local** con Mailhog
2. **Crea citas** de prueba para mañana
3. **Envía recordatorios** manualmente
4. **Verifica emails** en Mailhog
5. **Sube a producción** cuando esté satisfecho

---

**¿Preguntas o problemas? Revisa los logs en la consola de IntelliJ.**
