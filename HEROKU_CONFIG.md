# Configuración de Variables de Entorno en Heroku

## 🚀 Variables Requeridas para BunnyCure

Para que la aplicación funcione correctamente en Heroku, debes configurar las siguientes variables de entorno:

### 📧 Configuración de Email (Hostinger)

```bash
# Host SMTP de Hostinger
heroku config:set MAIL_HOST=smtp.hostinger.com

# Puerto SMTP
heroku config:set MAIL_PORT=587

# Usuario de email (tu email completo)
heroku config:set MAIL_USERNAME=contacto@bunnycure.cl

# Contraseña del email
heroku config:set MAIL_PASSWORD=tu_contraseña_aqui

# Email del remitente
heroku config:set MAIL_FROM=contacto@bunnycure.cl

# Habilitar email
heroku config:set MAIL_ENABLED=true
```

### 📱 WhatsApp

```bash
# Número de WhatsApp (sin +, solo dígitos)
heroku config:set WHATSAPP_NUMBER=56964499995
```

### 🔐 Seguridad (Administrador)

```bash
# Usuario administrador
heroku config:set ADMIN_USERNAME=admin

# Contraseña del administrador (¡cámbiala!)
heroku config:set ADMIN_PASSWORD=tu_contraseña_segura_aqui
```

### 🗄️ Base de Datos

**PostgreSQL se configura automáticamente** cuando agregas el addon de Heroku Postgres:

```bash
heroku addons:create heroku-postgresql:mini
```

Heroku inyecta automáticamente las variables: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`

---

## 📋 Verificar Variables Configuradas

Para ver todas las variables de entorno configuradas:

```bash
heroku config
```

Para ver una variable específica:

```bash
heroku config:get MAIL_USERNAME
```

---

## 🔧 Comandos Útiles

### Ver logs en tiempo real
```bash
heroku logs --tail
```

### Ver solo errores
```bash
heroku logs --tail | grep ERROR
```

### Reiniciar la aplicación
```bash
heroku restart
```

### Abrir la aplicación
```bash
heroku open
```

### Ver información de la base de datos
```bash
heroku pg:info
```

### Ejecutar migraciones de Flyway manualmente (si es necesario)
```bash
heroku run bash
./mvnw flyway:migrate
```

---

## ⚠️ Solución de Problemas Comunes

### Error: "Authentication failed" en emails

**Causa:** Las credenciales de email no están configuradas o son incorrectas.

**Solución:**
1. Verifica que `MAIL_USERNAME` y `MAIL_PASSWORD` estén configuradas:
   ```bash
   heroku config:get MAIL_USERNAME
   heroku config:get MAIL_PASSWORD
   ```

2. Si no están configuradas, agrégalas:
   ```bash
   heroku config:set MAIL_USERNAME=contacto@bunnycure.cl
   heroku config:set MAIL_PASSWORD=tu_contraseña
   ```

3. Asegúrate de que la contraseña sea correcta (sin espacios extra)

4. Reinicia la aplicación:
   ```bash
   heroku restart
   ```

### Error: "Database connection failed"

**Causa:** PostgreSQL no está configurado.

**Solución:**
```bash
heroku addons:create heroku-postgresql:mini
heroku restart
```

### La aplicación no inicia

**Solución:**
1. Ver los logs:
   ```bash
   heroku logs --tail
   ```

2. Verificar el Procfile:
   ```bash
   cat Procfile
   ```
   
   Debe contener:
   ```
   web: java -Dserver.port=$PORT -Dspring.profiles.active=heroku $JAVA_OPTS -jar target/bunnycure-*.jar
   ```

3. Asegurarte de que el jar se compiló correctamente:
   ```bash
   ./mvnw clean package -DskipTests
   git add .
   git commit -m "Rebuild jar"
   git push heroku main
   ```

---

## 🎯 Checklist de Despliegue

Antes de hacer push a Heroku, verifica:

- [ ] Todas las variables de entorno están configuradas
- [ ] PostgreSQL addon está instalado
- [ ] El código compila sin errores: `./mvnw clean package`
- [ ] Las migraciones de Flyway están en `src/main/resources/db/migration/`
- [ ] El Procfile está configurado correctamente
- [ ] El archivo `.gitignore` está actualizado
- [ ] No hay credenciales hardcodeadas en el código

---

## 🔄 Proceso de Despliegue

1. **Compilar y probar localmente:**
   ```bash
   ./mvnw clean package
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. **Commit de cambios:**
   ```bash
   git add .
   git commit -m "Tu mensaje de commit"
   ```

3. **Push a Heroku:**
   ```bash
   git push heroku main
   ```

4. **Verificar que todo funcione:**
   ```bash
   heroku logs --tail
   heroku open
   ```

---

## 📞 Contacto

Si tienes problemas, revisa los logs con `heroku logs --tail` y busca los mensajes de ERROR.
