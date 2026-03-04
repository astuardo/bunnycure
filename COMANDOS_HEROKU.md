# 🚀 Comandos Rápidos de Heroku - BunnyCure

## 📋 Configuración Inicial

```bash
# Verificar variables de entorno
heroku config

# Configurar email (Hostinger)
heroku config:set MAIL_HOST=smtp.hostinger.com
heroku config:set MAIL_PORT=587
heroku config:set MAIL_USERNAME=contacto@bunnycure.cl
heroku config:set MAIL_PASSWORD="tu_contraseña"
heroku config:set MAIL_FROM=contacto@bunnycure.cl
heroku config:set MAIL_ENABLED=true

# Configurar admin
heroku config:set ADMIN_USERNAME=admin
heroku config:set ADMIN_PASSWORD="contraseña_segura"

# Reiniciar
heroku restart
```

---

## 📊 Monitoreo

```bash
# Ver logs en tiempo real
heroku logs --tail

# Ver solo errores
heroku logs --tail | findstr ERROR

# Ver logs de email
heroku logs --tail | findstr MAIL

# Ver logs de recordatorios
heroku logs --tail | findstr REMINDER

# Ver últimas 200 líneas
heroku logs -n 200
```

---

## 🔧 Despliegue

```bash
# Compilar
./mvnw clean package -DskipTests

# Commit
git add .
git commit -m "Tu mensaje"

# Push a Heroku
git push heroku main

# Ver estado del build
heroku releases

# Rollback si algo sale mal
heroku rollback
```

---

## 🗄️ Base de Datos

```bash
# Ver info de la BD
heroku pg:info

# Backup
heroku pg:backups:capture

# Ver backups
heroku pg:backups

# Conectar a la BD (requiere psql instalado)
heroku pg:psql

# Reset de BD (¡CUIDADO! Borra todos los datos)
heroku pg:reset DATABASE_URL
```

---

## 🔍 Debug

```bash
# Ver dyno status
heroku ps

# Escalar dynos
heroku ps:scale web=1

# Reiniciar dyno específico
heroku ps:restart web.1

# Ejecutar comando en Heroku
heroku run bash
heroku run java -version
```

---

## 🌐 Dominios

```bash
# Ver dominios
heroku domains

# Agregar dominio personalizado
heroku domains:add admin.bunnycure.cl

# Ver info SSL
heroku certs:info
```

---

## ⚡ Atajos Útiles

```bash
# Abrir app en navegador
heroku open

# Abrir dashboard de Heroku
heroku dashboard

# Ver addons instalados
heroku addons

# Ver métricas
heroku logs --tail --ps web
```

---

## 🆘 Solución de Problemas

### App no inicia
```bash
heroku logs --tail
heroku ps
heroku restart
```

### Error de email
```bash
heroku config:get MAIL_USERNAME
heroku config:get MAIL_HOST
heroku logs --tail | findstr MAIL
```

### BD no conecta
```bash
heroku pg:info
heroku config | findstr DATABASE
```

### Revisar build
```bash
heroku builds
heroku builds:info LAST_BUILD_ID
```

---

## 📝 Variables de Entorno Importantes

```bash
# Ver variable específica
heroku config:get MAIL_USERNAME

# Eliminar variable
heroku config:unset VARIABLE_NAME

# Ver todas
heroku config

# Exportar a archivo (Git Bash)
heroku config -s > .env.heroku
```

---

## 🔐 Seguridad

```bash
# Ver quién tiene acceso
heroku access

# Agregar colaborador
heroku access:add email@example.com

# Rotar credenciales de BD
heroku pg:credentials:rotate DATABASE_URL
```

---

## 💰 Costos

```bash
# Ver facturación
heroku invoice

# Ver uso actual
heroku usage
```

---

## 🎯 Checklist Diario

- [ ] `heroku logs --tail` - Ver si hay errores
- [ ] `heroku ps` - Verificar que los dynos estén up
- [ ] `heroku pg:info` - Verificar BD (conexiones, tamaño)
- [ ] Probar la aplicación: `heroku open`

---

## 📞 Ayuda

```bash
# Ayuda general
heroku help

# Ayuda de comando específico
heroku help logs
heroku help config
```
