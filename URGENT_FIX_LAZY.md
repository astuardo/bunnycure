# ⚠️ PROBLEMA: @Lazy no se deployó

## Error Actual en Heroku
```
Error creating bean with name 'jwtAuthenticationFilter': 
Is there an unresolvable circular reference?
```

## Causa
El archivo `SecurityConfig.java` en Heroku **NO tiene** el `@Lazy`.

## Fix INMEDIATO

### Opción 1: Script Automático (RECOMENDADO)
```bash
.\quick-fix-lazy.bat
```

### Opción 2: Manual
```bash
cd C:\Users\alfre\IdeaProjects\bunnycure

# Verificar que SecurityConfig tiene @Lazy
type src\main\java\cl\bunnycure\config\SecurityConfig.java | findstr "@Lazy"
# Debe mostrar: @Lazy

# Add y commit
git add src\main\java\cl\bunnycure\config\SecurityConfig.java
git commit -m "fix: add @Lazy to break circular dependency"
git push heroku main

# Esperar y verificar
timeout /t 20
heroku logs --tail --app bunnycure-api
```

## Verificar que el Fix Funciona

### 1. Ver logs
```bash
heroku logs --tail --app bunnycure-api
```

### Buscar
✅ **CORRECTO:** `Started BunnycureApplication in X.XX seconds`  
❌ **ERROR:** `circular reference`

### 2. Test API
```powershell
# Login test
$body = @{username="admin"; password="tu_pass"} | ConvertTo-Json
try {
    $r = Invoke-RestMethod -Uri "https://bunnycure-04c4c179be8f.herokuapp.com/api/auth/login" -Method Post -ContentType "application/json" -Body $body
    Write-Host "✓ LOGIN OK - Token recibido" -ForegroundColor Green
    $r.data.token
} catch {
    Write-Host "✗ ERROR: $_" -ForegroundColor Red
}
```

## ¿Por qué pasó esto?

El commit anterior no incluyó el cambio de `@Lazy` en SecurityConfig.java.

Posibles razones:
1. El archivo no estaba staged cuando se hizo commit
2. El cambio se hizo después del commit
3. Hubo un conflict o merge que revirtió el cambio

## Verificación Pre-Deploy

Antes de hacer push, SIEMPRE verifica:

```bash
# Ver qué archivos están staged
git status

# Ver el contenido del cambio
git diff --staged src\main\java\cl\bunnycure\config\SecurityConfig.java

# Buscar @Lazy en el diff
git diff --staged src\main\java\cl\bunnycure\config\SecurityConfig.java | findstr "@Lazy"
```

## Estado Esperado de SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Environment env;
    private final PasswordChangeAuthenticationSuccessHandler passwordChangeSuccessHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    
    // @Lazy rompe la referencia circular:
    // SecurityConfig → JwtAuthenticationFilter → UserService → PasswordEncoder → SecurityConfig
    @Lazy  // ← ESTO DEBE ESTAR AQUÍ
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
```

## Timeline

1. ✅ Implementé JWT
2. ❌ Error: circular dependency  
3. ✅ Agregué `@Lazy` a SecurityConfig.java local
4. ❌ Push no incluyó el cambio
5. ⏳ **AHORA: Re-deploy con quick-fix-lazy.bat**

## Ejecuta el Fix AHORA

```bash
.\quick-fix-lazy.bat
```

Después de 30 segundos deberías ver:
```
Started BunnycureApplication in X.XX seconds
```

¡Sin errores de circular reference!
