# Implementación de Cambio de Contraseña en API REST

## ✅ Implementación Completada

### 1. **User Model** (`User.java`)
Se agregó el campo `passwordChangeRequired`:
```java
@Column(nullable = false)
@Builder.Default
private boolean passwordChangeRequired = false;
```

### 2. **DTO Request** (`ChangePasswordRequest.java`)
Actualizado con validaciones completas:
- `currentPassword`: @NotBlank
- `newPassword`: @NotBlank, @Size(min=8), @Pattern (mayúscula+minúscula+número)
- `confirmPassword`: @NotBlank

### 3. **UserService** (`UserService.java`)
#### Métodos agregados/actualizados:

**Nuevo método:**
```java
@Transactional
public void markPasswordChangeRequired(Long userId)
```
- Marca que el usuario debe cambiar su contraseña

**Actualizado:**
```java
public User createUser(...)
```
- Ahora marca `passwordChangeRequired = true` por defecto para usuarios nuevos

**Actualizado:**
```java
public void changePassword(String username, String oldPassword, String newPassword)
```
- Ahora pone `passwordChangeRequired = false` después del cambio exitoso

### 4. **AuthApiController** (`AuthApiController.java`)
#### Método de login actualizado:
- Verifica `user.isPasswordChangeRequired()` 
- Setea `requiresPasswordChange` en `LoginResponse`

#### Nuevo endpoint: `PUT /api/auth/change-password`
**Request:**
```json
{
  "currentPassword": "OldPass123",
  "newPassword": "NewPass456",
  "confirmPassword": "NewPass456"
}
```

**Validaciones implementadas:**
1. Usuario autenticado
2. `confirmPassword` == `newPassword`
3. `newPassword` != `currentPassword`
4. `newPassword` no sea "changeme", "admin", o "password"
5. `currentPassword` sea correcta (validado por UserService)
6. Validaciones de Bean Validation (@Size, @Pattern)

**Respuesta exitosa (200):**
```json
{
  "success": true,
  "data": "Contraseña actualizada exitosamente",
  "timestamp": "2024-01-15T10:30:00"
}
```

**Errores implementados:**
- `NOT_AUTHENTICATED` (401): Usuario no autenticado
- `PASSWORDS_NOT_MATCH` (400): confirmPassword != newPassword
- `PASSWORD_SAME` (400): newPassword == currentPassword
- `WEAK_PASSWORD` (400): Contraseña débil común
- `INVALID_PASSWORD` (401): currentPassword incorrecta
- `PASSWORD_CHANGE_ERROR` (500): Error general

### 5. **SecurityConfig** (`SecurityConfig.java`)
✅ Ya configurado correctamente:
- `/api/auth/login` y `/api/auth/logout` → Público
- `/api/auth/**` → Autenticado (incluye `/api/auth/change-password`)

## 🔧 Estructura de Archivos Modificados

```
src/main/java/cl/bunnycure/
├── domain/
│   └── model/
│       └── User.java                          ✅ Campo passwordChangeRequired
├── service/
│   └── UserService.java                       ✅ Métodos actualizados
└── web/
    ├── controller/
    │   └── AuthApiController.java             ✅ Endpoint change-password
    └── dto/
        └── ChangePasswordRequest.java         ✅ Validaciones completas
```

## 📝 Notas Importantes

### Base de Datos
⚠️ **PENDIENTE**: No se creó migración Liquibase para el campo `passwordChangeRequired`
- El campo se agregó al modelo Java
- Debe agregarse manualmente a la BD antes de ejecutar
- Valor por defecto: `false`

### Flujo de Trabajo
1. **Crear usuario nuevo** → `passwordChangeRequired = true`
2. **Login** → Responde con `requiresPasswordChange: true`
3. **Cambiar contraseña** → `passwordChangeRequired = false`
4. **Login posterior** → Responde con `requiresPasswordChange: false`

### Seguridad
- Las validaciones están tanto en el DTO (@Valid) como en lógica de negocio
- Contraseñas hasheadas con BCrypt
- No se permite reutilizar contraseña actual
- Bloqueadas contraseñas débiles comunes

## 🧪 Pruebas Sugeridas

### Test 1: Login con usuario nuevo
```bash
POST /api/auth/login
{
  "username": "newuser",
  "password": "temporal"
}
# Debe retornar: requiresPasswordChange: true
```

### Test 2: Cambio de contraseña exitoso
```bash
PUT /api/auth/change-password
{
  "currentPassword": "temporal",
  "newPassword": "NewSecure123",
  "confirmPassword": "NewSecure123"
}
# Debe retornar: success: true
```

### Test 3: Validación de contraseñas no coinciden
```bash
PUT /api/auth/change-password
{
  "currentPassword": "temporal",
  "newPassword": "NewSecure123",
  "confirmPassword": "Different123"
}
# Debe retornar: errorCode: PASSWORDS_NOT_MATCH
```

### Test 4: Contraseña actual incorrecta
```bash
PUT /api/auth/change-password
{
  "currentPassword": "wrong",
  "newPassword": "NewSecure123",
  "confirmPassword": "NewSecure123"
}
# Debe retornar: errorCode: INVALID_PASSWORD
```

### Test 5: Contraseña débil
```bash
PUT /api/auth/change-password
{
  "currentPassword": "temporal",
  "newPassword": "changeme",
  "confirmPassword": "changeme"
}
# Debe retornar: errorCode: WEAK_PASSWORD
```

## ✨ Características Implementadas

✅ Campo `passwordChangeRequired` en User model  
✅ Validaciones completas en DTO  
✅ Método `markPasswordChangeRequired()` en UserService  
✅ `createUser()` marca passwordChangeRequired por defecto  
✅ `changePassword()` actualiza passwordChangeRequired  
✅ Login verifica y retorna estado en LoginResponse  
✅ Endpoint `PUT /api/auth/change-password`  
✅ Validaciones de negocio (contraseña igual, débil, etc.)  
✅ Códigos de error consistentes  
✅ Logging adecuado  
✅ SecurityConfig correcto  
✅ Documentación Swagger/OpenAPI  

## 🚀 Próximos Pasos

1. **Crear migración Liquibase** para agregar columna `password_change_required`
2. **Actualizar usuarios existentes** si es necesario
3. **Pruebas de integración** con Postman/curl
4. **Documentar en Swagger UI** (ya tiene anotaciones)
5. **Considerar timeout** para cambio obligatorio de contraseña
