# Ejemplos de Uso - API de Usuarios

## Autenticación Previa

Primero debe autenticarse como ADMIN:

```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "tupassword"
}
```

Esto retorna una sesión (cookie JSESSIONID) que debe incluirse en todas las peticiones siguientes.

---

## 1. Listar Todos los Usuarios

```bash
GET http://localhost:8080/api/users
```

**Respuesta:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "username": "admin",
      "fullName": "Administrador Principal",
      "email": "admin@bunnycure.cl",
      "role": "ADMIN",
      "enabled": true
    }
  ],
  "error": null,
  "timestamp": "2024-01-15T10:30:00"
}
```

---

## 2. Obtener Usuario por ID

```bash
GET http://localhost:8080/api/users/1
```

**Respuesta:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "admin",
    "fullName": "Administrador Principal",
    "email": "admin@bunnycure.cl",
    "role": "ADMIN",
    "enabled": true
  },
  "error": null,
  "timestamp": "2024-01-15T10:30:00"
}
```

---

## 3. Crear Nuevo Usuario

```bash
POST http://localhost:8080/api/users
Content-Type: application/json

{
  "username": "nuevo_admin",
  "password": "Secure123",
  "fullName": "Nuevo Administrador",
  "email": "nuevo@bunnycure.cl"
}
```

**Validaciones:**
- Username: mínimo 3 caracteres, máximo 50
- Password: mínimo 8 caracteres, debe tener mayúscula, minúscula y número
- FullName: máximo 100 caracteres
- Email: formato válido (opcional)

**Respuesta Exitosa (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": 2,
    "username": "nuevo_admin",
    "fullName": "Nuevo Administrador",
    "email": "nuevo@bunnycure.cl",
    "role": "ADMIN",
    "enabled": true
  },
  "error": null,
  "timestamp": "2024-01-15T10:35:00"
}
```

**Respuesta de Error (400 Bad Request):**
```json
{
  "success": false,
  "data": null,
  "error": {
    "message": "El usuario ya existe",
    "errorCode": "USER_CREATION_ERROR"
  },
  "timestamp": "2024-01-15T10:35:00"
}
```

---

## 4. Actualizar Usuario

```bash
PUT http://localhost:8080/api/users/2
Content-Type: application/json

{
  "fullName": "Nuevo Administrador Actualizado",
  "email": "admin.nuevo@bunnycure.cl"
}
```

**Respuesta:**
```json
{
  "success": true,
  "data": {
    "id": 2,
    "username": "nuevo_admin",
    "fullName": "Nuevo Administrador Actualizado",
    "email": "admin.nuevo@bunnycure.cl",
    "role": "ADMIN",
    "enabled": true
  },
  "error": null,
  "timestamp": "2024-01-15T10:40:00"
}
```

---

## 5. Activar/Desactivar Usuario

```bash
PUT http://localhost:8080/api/users/2/toggle-enabled
```

**Respuesta:**
```json
{
  "success": true,
  "data": {
    "id": 2,
    "username": "nuevo_admin",
    "fullName": "Nuevo Administrador",
    "email": "nuevo@bunnycure.cl",
    "role": "ADMIN",
    "enabled": false
  },
  "error": null,
  "timestamp": "2024-01-15T10:45:00"
}
```

**Error - Último Usuario Activo:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "message": "No se puede desactivar el último usuario activo",
    "errorCode": "CANNOT_DISABLE_LAST_ACTIVE_USER"
  },
  "timestamp": "2024-01-15T10:45:00"
}
```

---

## 6. Cambiar Contraseña

```bash
PUT http://localhost:8080/api/users/2/change-password
Content-Type: application/json

{
  "newPassword": "NewSecure456"
}
```

**Validaciones:**
- Mínimo 8 caracteres
- Debe tener mayúscula, minúscula y número

**Respuesta:**
```json
{
  "success": true,
  "data": "Contraseña actualizada exitosamente",
  "error": null,
  "timestamp": "2024-01-15T10:50:00"
}
```

---

## 7. Eliminar Usuario

```bash
DELETE http://localhost:8080/api/users/2
```

**Respuesta Exitosa (204 No Content):**
Sin contenido en el cuerpo

**Error - Eliminar Propio Usuario:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "message": "No puede eliminar su propio usuario",
    "errorCode": "CANNOT_DELETE_SELF"
  },
  "timestamp": "2024-01-15T10:55:00"
}
```

**Error - Último Usuario:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "message": "No se puede eliminar el único usuario administrador",
    "errorCode": "CANNOT_DELETE_LAST_USER"
  },
  "timestamp": "2024-01-15T10:55:00"
}
```

---

## Testing con cURL

### Crear Usuario
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=tu_session_id" \
  -d '{
    "username": "test_admin",
    "password": "Test1234",
    "fullName": "Usuario de Prueba",
    "email": "test@bunnycure.cl"
  }'
```

### Listar Usuarios
```bash
curl http://localhost:8080/api/users \
  -H "Cookie: JSESSIONID=tu_session_id"
```

### Cambiar Contraseña
```bash
curl -X PUT http://localhost:8080/api/users/2/change-password \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=tu_session_id" \
  -d '{
    "newPassword": "NewPass123"
  }'
```

---

## Notas Importantes

1. **Autenticación Requerida**: Todas las peticiones requieren estar autenticado
2. **Rol ADMIN Requerido**: Solo usuarios con rol ADMIN pueden acceder
3. **Cookie de Sesión**: Debe incluir la cookie JSESSIONID en cada petición
4. **Validación de Contraseñas**: Siempre se valida formato seguro
5. **Protección de Datos**: No se elimina el último usuario ni se permite auto-eliminación
