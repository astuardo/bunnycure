# FIX: Circular Dependency en Spring Boot

## 🔴 Problema Detectado

La aplicación en Heroku está crasheando con el siguiente error:

```
UnsatisfiedDependencyException: Error creating bean with name 'jwtAuthenticationFilter'...
Is there an unresolvable circular reference?
```

## 🔍 Análisis del Ciclo

El ciclo de dependencias detectado:

```
JwtAuthenticationFilter (necesita) 
    ↓
UserDetailsService (UserService) (necesita)
    ↓
PasswordEncoder (necesita)
    ↓
SecurityConfig (necesita)
    ↓
JwtAuthenticationFilter ← ¡CICLO!
```

### Detalles Técnicos:

1. **JwtAuthenticationFilter** (línea 33): requiere `UserDetailsService`
2. **UserService** implementa `UserDetailsService` (línea 22): requiere `PasswordEncoder`
3. **PasswordEncoder** se define en **SecurityConfig** como `@Bean` (línea 175)
4. **SecurityConfig** (línea 34): requiere `JwtAuthenticationFilter` con `@Lazy`

El problema es que aunque SecurityConfig tiene `@Lazy` en JwtAuthenticationFilter, el ciclo se cierra porque UserService requiere PasswordEncoder SIN lazy loading.

## ✅ Solución Implementada

Agregar `@Lazy` al PasswordEncoder en el constructor de UserService:

```java
@Service
public class UserService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor explícito con @Lazy en PasswordEncoder
    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    // resto del código...
}
```

### Por qué funciona:

- `@Lazy` en el constructor hace que Spring no intente resolver el PasswordEncoder inmediatamente
- Solo se crea cuando realmente se necesita (en métodos como `createUser`, `changePassword`)
- Rompe el ciclo de inicialización circular

## 📝 Cambios Realizados

**Archivo modificado:**
- `src/main/java/cl/bunnycure/service/UserService.java`

**Cambios:**
1. Removido `@RequiredArgsConstructor` de Lombok
2. Agregado constructor explícito con `@Lazy` en el parámetro `PasswordEncoder`
3. Agregado import: `org.springframework.context.annotation.Lazy`

## 🚀 Para Desplegar

Ejecuta el script:

```bash
fix-circular-dependency.bat
```

O manualmente:

```bash
# 1. Compilar
mvnw.cmd clean package -DskipTests

# 2. Commit
git add src\main\java\cl\bunnycure\service\UserService.java
git commit -m "Fix circular dependency: add @Lazy to PasswordEncoder in UserService"

# 3. Deploy
git push heroku main
```

## ✅ Verificación

Después del deploy, verifica:

1. App funcionando: https://bunnycure-f6b79f1c42e8.herokuapp.com
2. Logs sin errores: `heroku logs --tail -a bunnycure`
3. API funcionando: Test de login

## 📚 Referencias

- Spring Boot Circular Dependencies: https://www.baeldung.com/circular-dependencies-in-spring
- @Lazy Annotation: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Lazy.html
