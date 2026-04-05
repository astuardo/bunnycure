# API REST de Gestión de Usuarios - Resumen de Implementación

## ✅ Archivos Creados

### DTOs (src/main/java/cl/bunnycure/web/dto/)
1. **CreateUserRequest.java**
   - Username: @NotBlank, @Size(3-50)
   - Password: @NotBlank, @Size(min=8), @Pattern(mayúscula, minúscula, número)
   - FullName: @NotBlank, @Size(max=100)
   - Email: @Email (opcional)

2. **UpdateUserRequest.java**
   - FullName: @NotBlank, @Size(max=100)
   - Email: @Email (opcional)

3. **ChangePasswordRequest.java**
   - NewPassword: @NotBlank, @Size(min=8), @Pattern(mayúscula, minúscula, número)

### Controller (src/main/java/cl/bunnycure/web/controller/)
4. **UserApiController.java**
   - Patrón ApiResponse<T> para todas las respuestas
   - Documentación completa con Swagger/OpenAPI
   - Logging de operaciones
   - Manejo de errores con códigos apropiados

## 🔒 Endpoints Implementados

### GET /api/users
- Lista todos los usuarios del sistema
- Retorna: List<UserDto>
- Status: 200 OK

### GET /api/users/{id}
- Obtiene un usuario por ID
- Retorna: UserDto
- Status: 200 OK / 404 Not Found

### POST /api/users
- Crea un nuevo usuario
- Body: CreateUserRequest
- Retorna: UserDto
- Status: 201 Created / 400 Bad Request

### PUT /api/users/{id}
- Actualiza datos de usuario (fullName, email)
- Body: UpdateUserRequest
- Retorna: UserDto
- Status: 200 OK / 404 Not Found

### DELETE /api/users/{id}
- Elimina un usuario
- Validaciones:
  * No permitir eliminar el propio usuario
  * No permitir eliminar el último usuario
- Status: 204 No Content / 400 Bad Request / 404 Not Found

### PUT /api/users/{id}/toggle-enabled
- Activa/desactiva un usuario
- Validación: No permitir desactivar el último usuario activo
- Retorna: UserDto
- Status: 200 OK / 400 Bad Request / 404 Not Found

### PUT /api/users/{id}/change-password
- Cambia la contraseña de un usuario (solo ADMIN)
- Body: ChangePasswordRequest
- Retorna: mensaje de éxito
- Status: 200 OK / 404 Not Found

## 🛡️ Seguridad

### SecurityConfig.java - ACTUALIZADO ✅
```java
auth.requestMatchers("/api/users/**").hasRole("ADMIN");
```

- Todos los endpoints de /api/users/** requieren rol ADMIN
- Las rutas están protegidas a nivel de SecurityFilterChain
- Solo usuarios autenticados con rol ADMIN pueden acceder

## 📋 Validaciones de Negocio

### CreateUserRequest
- Username único (3-50 caracteres)
- Password segura (mínimo 8 caracteres, mayúscula, minúscula, número)
- FullName obligatorio (máximo 100 caracteres)
- Email válido y opcional

### Eliminar Usuario
- ❌ No permitir eliminar el propio usuario
- ❌ No permitir eliminar el último usuario administrador

### Desactivar Usuario
- ❌ No permitir desactivar el último usuario activo

## 🔧 Integración con UserService

El controller utiliza los métodos existentes en UserService:
- `findAll()` - Listar usuarios
- `findById(Long id)` - Obtener por ID
- `createUser(...)` - Crear usuario
- `updateUser(...)` - Actualizar usuario
- `deleteUser(Long id)` - Eliminar usuario
- `toggleEnabled(Long id)` - Cambiar estado
- `changePassword(Long id, String newPassword)` - Cambiar contraseña

## 📝 Estructura de Respuestas

Todas las respuestas usan el patrón ApiResponse<T>:

```json
{
  "success": true,
  "data": { /* UserDto */ },
  "error": null,
  "timestamp": "2024-01-15T10:30:00"
}
```

En caso de error:
```json
{
  "success": false,
  "data": null,
  "error": {
    "message": "Usuario no encontrado",
    "errorCode": "USER_NOT_FOUND"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

## 🎯 Códigos de Error

- `USER_NOT_FOUND` - Usuario no encontrado
- `USER_CREATION_ERROR` - Error al crear usuario
- `CANNOT_DELETE_SELF` - No puede eliminar su propio usuario
- `CANNOT_DELETE_LAST_USER` - No puede eliminar el último usuario
- `CANNOT_DISABLE_LAST_ACTIVE_USER` - No puede desactivar el último usuario activo

## 📚 Documentación OpenAPI

Cada endpoint incluye:
- `@Operation` - Descripción del endpoint
- `@ApiResponses` - Códigos de respuesta esperados
- `@Parameter` - Descripción de parámetros
- `@Tag` - Agrupación en Swagger UI

## ✅ Para Compilar y Probar

```bash
# Compilar
.\mvnw.cmd clean compile

# Ejecutar
.\mvnw.cmd spring-boot:run

# Acceder a Swagger UI (perfil local)
http://localhost:8080/swagger-ui.html
```

## 🔍 Verificación Manual

1. Iniciar la aplicación
2. Login como ADMIN
3. Probar endpoints desde Swagger UI o Postman
4. Verificar logs en consola con [API] prefix

## ⚠️ Notas Importantes

1. Todos los endpoints están protegidos - requieren autenticación y rol ADMIN
2. Las contraseñas se encriptan automáticamente con BCrypt
3. Los usuarios se crean por defecto con rol "ADMIN"
4. El UserService maneja las validaciones de negocio
5. Los DTOs validan la estructura de los datos de entrada
