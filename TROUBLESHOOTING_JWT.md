# Resolución de Problemas JWT - Referencia Circular

## ⚠️ PROBLEMA ACTUAL: @Lazy no se deployó

### Síntoma
```
Error creating bean with name 'jwtAuthenticationFilter': 
Is there an unresolvable circular reference?
```

### Causa
El archivo `SecurityConfig.java` que se deployó NO incluye `@Lazy`.

### Solución RÁPIDA
```bash
.\quick-fix-lazy.bat
```

Este script:
1. Verifica git status
2. Agrega SecurityConfig.java con @Lazy
3. Commit y push a Heroku
4. Espera y muestra logs

---

## Problema Encontrado (Heroku)

```
Error creating bean with name 'jwtAuthenticationFilter': 
Unsatisfied dependency expressed through constructor parameter 1: 
...
Is there an unresolvable circular reference?
```

### Causa
Referencia circular entre beans de Spring:

```
SecurityConfig 
    ↓ inyecta
JwtAuthenticationFilter 
    ↓ necesita
UserService (UserDetailsService)
    ↓ necesita  
PasswordEncoder
    ↓ definido en
SecurityConfig  ← CICLO!
```

### Solución Aplicada

Usar `@Lazy` en `SecurityConfig` para romper el ciclo:

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    // @Lazy rompe la referencia circular
    @Lazy
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    // ... resto del código
}
```

**¿Qué hace `@Lazy`?**
- Spring no crea `JwtAuthenticationFilter` inmediatamente
- Se crea cuando realmente se necesita (después de que PasswordEncoder existe)
- Rompe el ciclo de dependencias

---

## Otros Problemas Resueltos

### 1. Missing column password_change_required (Local H2)

**Solución:** Agregar migración V36 en `db/migration-h2/`

Ver: `FIX_LOCAL_DB.md`

---

## Deploy Actualizado

El script `deploy-jwt.bat` ahora incluye:
- ✅ Fix de referencia circular (`@Lazy`)
- ✅ Todas las migraciones necesarias
- ✅ Logging mejorado para debugging

```bash
.\deploy-jwt.bat
```

---

## Verificar Deploy Exitoso

### 1. Ver logs de Heroku
```bash
heroku logs --tail --app bunnycure-api
```

Buscar:
```
INFO  ... Started BunnycureApplication in X.XX seconds
```

### 2. Probar login API
```bash
curl -X POST https://bunnycure-api-7e41af10dd44.herokuapp.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"tu_password"}'
```

Deberías recibir:
```json
{
  "success": true,
  "data": {
    "user": {...},
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "requiresPasswordChange": false,
    "message": "Login exitoso"
  }
}
```

### 3. Verificar JWT funciona
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl -X GET https://bunnycure-api-7e41af10dd44.herokuapp.com/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## Archivos Modificados

### Fix Circular Dependency:
- ✅ `SecurityConfig.java` - Agregado `@Lazy`

### JWT Implementation:
- ✅ `AuthApiController.java` - Login con JWT + logging
- ✅ `LoginResponse.java` - Campo token
- ✅ `JwtAuthenticationFilter.java` - Logging detallado
- ✅ `application-heroku.properties` - Debug logging

### Database Migrations:
- ✅ `db/migration-h2/V36__add_password_change_required_to_users.sql`

### Scripts y Docs:
- ✅ `deploy-jwt.bat` - Deploy actualizado
- ✅ `reset-local-db.bat` - Limpiar BD local
- ✅ `IMPLEMENTACION_JWT.md` - Guía completa
- ✅ `EJEMPLOS_JWT_TESTING.md` - Tests
- ✅ `FIX_LOCAL_DB.md` - Fix BD local
- ✅ `TROUBLESHOOTING_JWT.md` - Este archivo

---

## Timeline de Resolución

1. ✅ Implementación JWT inicial
2. ❌ Error: Missing column (local H2)
   - ✅ Fix: Migración V36 para H2
3. ❌ Error: Circular dependency (Heroku)
   - ✅ Fix: `@Lazy` en SecurityConfig
4. ✅ Deploy exitoso esperado

---

## Lecciones Aprendidas

### Evitar Referencias Circulares
- Usar `@Lazy` cuando sea necesario
- Separar configuración en múltiples `@Configuration` classes
- Considerar usar `@Primary` o `@Qualifier` para beans alternativos

### Testing Local vs Producción
- Mantener migraciones sincronizadas (H2 y PostgreSQL)
- Probar builds completos antes de deploy
- Usar perfiles de Spring apropiadamente

### Debugging en Heroku
- Logs detallados son cruciales
- `heroku logs --tail` es tu amigo
- Agregar logging estratégico en puntos críticos

---

## Recursos

- **JWT Documentation:** `IMPLEMENTACION_JWT.md`
- **Testing Examples:** `EJEMPLOS_JWT_TESTING.md`
- **Local DB Fix:** `FIX_LOCAL_DB.md`
- **Deploy Script:** `deploy-jwt.bat`

---

## Contacto

Si encuentras más problemas:
1. Revisa los logs: `heroku logs --tail --app bunnycure-api`
2. Verifica configuración en este documento
3. Consulta documentación adicional en los archivos .md
