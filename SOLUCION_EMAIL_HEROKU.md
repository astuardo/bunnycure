# ⚠️ SOLUCIÓN: Error de Autenticación SMTP en Heroku

## 🔴 Problema Detectado

```
[MAIL-FAILED] Todos los intentos fallaron para astuardobonilla@gmail.com: Authentication failed
```

---

## 🎯 Causa Raíz

Las credenciales de email (MAIL_USERNAME y MAIL_PASSWORD) **NO están configuradas** o **son incorrectas** en las variables de entorno de Heroku.

---

## ✅ Solución Rápida

### Opción 1: Script Automático (Recomendado)

Ejecuta el script que he creado:

```cmd
configurar-heroku.cmd
```

Este script te guiará para configurar todas las variables de entorno necesarias.

---

### Opción 2: Manual

Ejecuta estos comandos uno por uno en tu terminal:

```bash
# 1. Configurar host SMTP
heroku config:set MAIL_HOST=smtp.hostinger.com

# 2. Configurar puerto
heroku config:set MAIL_PORT=587

# 3. Configurar usuario (tu email de Hostinger)
heroku config:set MAIL_USERNAME=contacto@bunnycure.cl

# 4. Configurar contraseña (reemplaza con tu contraseña real)
heroku config:set MAIL_PASSWORD=Cami#3119!!

# 5. Configurar email del remitente
heroku config:set MAIL_FROM=contacto@bunnycure.cl

# 6. Habilitar envío de emails
heroku config:set MAIL_ENABLED=true

# 7. Reiniciar la aplicación
heroku restart
```

---

## 🔍 Verificación

Después de configurar las variables, verifica que estén correctas:

```bash
# Ver todas las variables
heroku config

# Verificar una variable específica
heroku config:get MAIL_USERNAME
heroku config:get MAIL_HOST
```

Deberías ver algo como:

```
MAIL_HOST:         smtp.hostinger.com
MAIL_PORT:         587
MAIL_USERNAME:     contacto@bunnycure.cl
MAIL_PASSWORD:     [REDACTED]
MAIL_FROM:         contacto@bunnycure.cl
MAIL_ENABLED:      true
```

---

## 📊 Monitorear la Solución

Una vez configuradas las variables y reiniciada la app:

```bash
# Ver logs en tiempo real
heroku logs --tail

# Filtrar solo mensajes de email
heroku logs --tail | grep MAIL
```

Si todo está bien, deberías ver mensajes como:

```
[MAIL] Enviando a astuardobonilla@gmail.com
[MAIL] ✅ Email enviado exitosamente a astuardobonilla@gmail.com
```

---

## 🚨 Problemas Comunes

### 1. La contraseña tiene caracteres especiales

Si tu contraseña tiene caracteres especiales como `!`, `$`, `#`, `&`, etc., asegúrate de:

- En **CMD/PowerShell**: Rodea la contraseña con comillas dobles:
  ```bash
  heroku config:set MAIL_PASSWORD="Cami#3119!!"
  ```

- En **Bash/Terminal Linux/Mac**: Usa comillas simples:
  ```bash
  heroku config:set MAIL_PASSWORD='Cami#3119!!'
  ```

### 2. El email sigue fallando después de configurar

1. **Verifica las credenciales en Hostinger**:
   - Ve a tu panel de Hostinger
   - Verifica que el email `contacto@bunnycure.cl` existe
   - Verifica que la contraseña sea correcta
   - Asegúrate de que el email no esté bloqueado

2. **Verifica la configuración SMTP de Hostinger**:
   - Host: `smtp.hostinger.com`
   - Puerto: `587`
   - Encriptación: STARTTLS
   - Autenticación: Sí

3. **Reinicia la aplicación**:
   ```bash
   heroku restart
   ```

### 3. Verificar que Heroku use el perfil correcto

En el `Procfile`, asegúrate de que esté así:

```
web: java -Dserver.port=$PORT -Dspring.profiles.active=heroku $JAVA_OPTS -jar target/bunnycure-*.jar
```

El `-Dspring.profiles.active=heroku` es crucial para que use `application-heroku.properties`.

---

## 📝 Cambios Realizados en el Código

He actualizado los siguientes archivos:

1. **`application-heroku.properties`**:
   - ✅ Cambiado de Gmail a Hostinger
   - ✅ Agregadas todas las propiedades de email necesarias
   - ✅ Agregado `bunnycure.mail.from`
   - ✅ Agregado timeout y configuraciones SMTP

2. **`.gitignore`**:
   - ✅ Actualizado para no subir archivos sensibles

3. **Scripts creados**:
   - ✅ `configurar-heroku.cmd` - Script para configurar variables automáticamente
   - ✅ `HEROKU_CONFIG.md` - Documentación completa
   - ✅ `SOLUCION_EMAIL_HEROKU.md` - Este archivo

---

## 🔄 Próximos Pasos

1. **Configurar las variables de entorno** (usa el script o hazlo manual)
2. **Reiniciar Heroku**: `heroku restart`
3. **Monitorear los logs**: `heroku logs --tail`
4. **Probar enviando un email** desde la aplicación
5. **Verificar que llegue el email**

---

## 📞 Soporte

Si después de seguir estos pasos el problema persiste:

1. Verifica los logs completos: `heroku logs --tail > logs.txt`
2. Busca el error específico
3. Verifica que las credenciales de Hostinger sean correctas accediendo a su panel

---

## ✨ Resultado Esperado

Después de aplicar esta solución, los emails deberían enviarse correctamente:

```
2026-03-04T06:00:00.000Z  INFO 2 --- [bunnycure] [ure-scheduler-1] c.b.service.NotificationService : [MAIL] Enviando a astuardobonilla@gmail.com
2026-03-04T06:00:01.000Z  INFO 2 --- [bunnycure] [ure-scheduler-1] c.b.service.NotificationService : [MAIL] ✅ Email enviado exitosamente
```

¡Listo! 🎉
