# ERROR RESUELTO: Missing column password_change_required

## Problema
La base de datos local H2 no tiene la columna `password_change_required` porque faltaba la migración V36 en `db/migration-h2/`.

## Solución Aplicada

✅ **Creado:** `src/main/resources/db/migration-h2/V36__add_password_change_required_to_users.sql`

Esta migración agrega la columna necesaria a la tabla `users`.

---

## Cómo Arreglar tu Base de Datos Local

### Opción 1: Resetear la BD local (RECOMENDADO - más limpio)

```bash
# Ejecutar el script
.\reset-local-db.bat
```

Esto eliminará los archivos de H2 y al iniciar la app, Flyway creará todo desde cero con todas las migraciones.

### Opción 2: Aplicar solo la migración faltante (manual)

Si quieres mantener tus datos locales:

1. Inicia la aplicación
2. Ve a http://localhost:8080/h2-console
3. Conecta con:
   - JDBC URL: `jdbc:h2:file:./target/bunnycure-local`
   - User: `sa`
   - Password: (vacío)
4. Ejecuta el SQL:
   ```sql
   ALTER TABLE users
   ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT false;
   ```
5. Reinicia la aplicación

---

## Iniciar la Aplicación Local

### Desde IntelliJ (recomendado)
1. Abre `BunnycureApplication.java`
2. Run/Debug con perfil **`local`**
3. Verifica en consola que Flyway ejecuta V36

### Desde línea de comandos
```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Verificar que Funciona

Una vez iniciada la app, deberías ver en los logs:

```
INFO ... Flyway ...  Migrating schema "PUBLIC" to version "36 - add password change required to users"
INFO ... Flyway ...  Successfully applied 1 migration
```

---

## Deploy a Heroku (Producción)

**IMPORTANTE:** La base de datos de Heroku (PostgreSQL) ya tiene esta columna porque V36 existe en `db/migration/` (PostgreSQL).

El error solo era local (H2). Puedes deployar sin problemas:

```bash
.\deploy-jwt.bat
```

---

## Archivos Creados/Modificados

### Para JWT (listos para deploy):
- ✅ `AuthApiController.java` - Login con JWT
- ✅ `LoginResponse.java` - DTO con token
- ✅ `JwtAuthenticationFilter.java` - Logging mejorado
- ✅ `application-heroku.properties` - Debug logging
- ✅ `IMPLEMENTACION_JWT.md` - Documentación
- ✅ `EJEMPLOS_JWT_TESTING.md` - Tests
- ✅ `deploy-jwt.bat` - Script de deploy

### Para arreglar local:
- ✅ `db/migration-h2/V36__add_password_change_required_to_users.sql` - Migración faltante
- ✅ `reset-local-db.bat` - Script para limpiar BD local
- ✅ `FIX_LOCAL_DB.md` - Esta guía

---

## Resumen Rápido

1. **Ejecuta:** `.\reset-local-db.bat`
2. **Inicia la app** desde IntelliJ con perfil `local`
3. **Verifica** que carga sin errores
4. **Deploy a Heroku:** `.\deploy-jwt.bat`
5. **Prueba JWT** desde móvil con los ejemplos en `EJEMPLOS_JWT_TESTING.md`

---

## Si Tienes Problemas

### Error persiste después de resetear BD
- Verifica que el archivo V36 existe en `db/migration-h2/`
- Asegúrate de que el perfil activo es `local`
- Revisa los logs de Flyway en la consola

### No se puede conectar a H2 Console
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./target/bunnycure-local`
- User: `sa`
- Password: (vacío)

### Flyway dice "schema validation failed"
- Elimina completamente: `target/bunnycure-local*`
- Borra `target/classes` y recompila
- Ejecuta: `mvnw.cmd clean compile`
