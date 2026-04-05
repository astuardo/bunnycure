# Ejemplos de Uso - API de Cambio de Contraseña

## 🔐 Endpoint: Change Password

**URL:** `PUT /api/auth/change-password`  
**Autenticación:** Requerida (Cookie de sesión)  
**Content-Type:** `application/json`

---

## 📋 Ejemplos con cURL

### 1. Cambio Exitoso de Contraseña

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "NewSecurePass456",
    "confirmPassword": "NewSecurePass456"
  }'
```

**Respuesta (200 OK):**
```json
{
  "success": true,
  "data": "Contraseña actualizada exitosamente",
  "timestamp": "2024-01-15T10:30:00.123"
}
```

---

### 2. Error: Contraseñas No Coinciden

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "NewSecurePass456",
    "confirmPassword": "DifferentPass789"
  }'
```

**Respuesta (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "message": "Las contraseñas no coinciden",
    "errorCode": "PASSWORDS_NOT_MATCH"
  },
  "timestamp": "2024-01-15T10:31:00.456"
}
```

---

### 3. Error: Contraseña Actual Incorrecta

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "WrongPassword",
    "newPassword": "NewSecurePass456",
    "confirmPassword": "NewSecurePass456"
  }'
```

**Respuesta (401 Unauthorized):**
```json
{
  "success": false,
  "error": {
    "message": "La contraseña actual es incorrecta",
    "errorCode": "INVALID_PASSWORD"
  },
  "timestamp": "2024-01-15T10:32:00.789"
}
```

---

### 4. Error: Nueva Contraseña Igual a la Actual

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "OldPassword123",
    "confirmPassword": "OldPassword123"
  }'
```

**Respuesta (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "message": "La nueva contraseña debe ser diferente a la actual",
    "errorCode": "PASSWORD_SAME"
  },
  "timestamp": "2024-01-15T10:33:00.012"
}
```

---

### 5. Error: Contraseña Débil Común

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "changeme",
    "confirmPassword": "changeme"
  }'
```

**Respuesta (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "message": "No puede usar 'changeme' como contraseña",
    "errorCode": "WEAK_PASSWORD"
  },
  "timestamp": "2024-01-15T10:34:00.345"
}
```

**Contraseñas bloqueadas:**
- `changeme`
- `admin`
- `password`

---

### 6. Error: Validación de Formato (Bean Validation)

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "short",
    "confirmPassword": "short"
  }'
```

**Respuesta (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "message": "Errores de validación",
    "errorCode": "VALIDATION_ERRORS",
    "fieldErrors": [
      {
        "field": "newPassword",
        "message": "La contraseña debe tener al menos 8 caracteres"
      }
    ]
  },
  "timestamp": "2024-01-15T10:35:00.678"
}
```

---

### 7. Error: Sin Mayúsculas/Minúsculas/Números

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "alllowercase",
    "confirmPassword": "alllowercase"
  }'
```

**Respuesta (400 Bad Request):**
```json
{
  "success": false,
  "error": {
    "message": "Errores de validación",
    "errorCode": "VALIDATION_ERRORS",
    "fieldErrors": [
      {
        "field": "newPassword",
        "message": "La contraseña debe contener al menos una mayúscula, una minúscula y un número"
      }
    ]
  },
  "timestamp": "2024-01-15T10:36:00.901"
}
```

---

### 8. Error: No Autenticado

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "NewSecurePass456",
    "confirmPassword": "NewSecurePass456"
  }'
```

**Respuesta (401 Unauthorized):**
```json
{
  "success": false,
  "error": {
    "message": "No autenticado",
    "errorCode": "NOT_AUTHENTICATED"
  },
  "timestamp": "2024-01-15T10:37:00.234"
}
```

---

## 🔄 Flujo Completo con Login

### Paso 1: Login (obtener sesión)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "username": "admin",
    "password": "OldPassword123"
  }'
```

**Respuesta:**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "username": "admin",
      "fullName": "Administrador",
      "email": "admin@example.com",
      "role": "ADMIN",
      "enabled": true
    },
    "requiresPasswordChange": true,
    "message": "Debe cambiar su contraseña"
  },
  "timestamp": "2024-01-15T10:38:00.567"
}
```

**Nota:** Si `requiresPasswordChange` es `true`, el usuario debe cambiar su contraseña.

---

### Paso 2: Cambiar Contraseña

```bash
curl -X PUT http://localhost:8080/api/auth/change-password \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "currentPassword": "OldPassword123",
    "newPassword": "NewSecurePass456",
    "confirmPassword": "NewSecurePass456"
  }'
```

**Respuesta:**
```json
{
  "success": true,
  "data": "Contraseña actualizada exitosamente",
  "timestamp": "2024-01-15T10:39:00.890"
}
```

---

### Paso 3: Verificar Cambio (Login Nuevamente)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "username": "admin",
    "password": "NewSecurePass456"
  }'
```

**Respuesta:**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "username": "admin",
      "fullName": "Administrador",
      "email": "admin@example.com",
      "role": "ADMIN",
      "enabled": true
    },
    "requiresPasswordChange": false,
    "message": "Login exitoso"
  },
  "timestamp": "2024-01-15T10:40:00.123"
}
```

**Nota:** Ahora `requiresPasswordChange` es `false`.

---

## 📋 Validaciones Implementadas

### 1. **Validaciones de Bean Validation (@Valid)**
- ✅ `currentPassword` no vacío
- ✅ `newPassword` no vacío
- ✅ `newPassword` mínimo 8 caracteres
- ✅ `newPassword` con patrón (mayúscula + minúscula + número)
- ✅ `confirmPassword` no vacío

### 2. **Validaciones de Lógica de Negocio**
- ✅ Usuario autenticado
- ✅ `confirmPassword` == `newPassword`
- ✅ `newPassword` != `currentPassword`
- ✅ `newPassword` no sea contraseña débil común
- ✅ `currentPassword` sea correcta (validado con hash BCrypt)

---

## 🎯 Códigos de Error

| Código                 | HTTP Status | Descripción                           |
|------------------------|-------------|---------------------------------------|
| `NOT_AUTHENTICATED`    | 401         | Usuario no autenticado                |
| `INVALID_PASSWORD`     | 401         | Contraseña actual incorrecta          |
| `PASSWORDS_NOT_MATCH`  | 400         | confirmPassword != newPassword        |
| `PASSWORD_SAME`        | 400         | newPassword == currentPassword        |
| `WEAK_PASSWORD`        | 400         | Contraseña débil común                |
| `VALIDATION_ERRORS`    | 400         | Errores de validación de Bean         |
| `PASSWORD_CHANGE_ERROR`| 500         | Error general al cambiar contraseña   |

---

## 🧪 Testing con Postman

### Configuración
1. **Environment Variables:**
   - `baseUrl`: `http://localhost:8080`
   
2. **Collection Variables:**
   - `sessionCookie`: Se actualiza automáticamente después del login

### Pre-request Script (Login)
```javascript
// Guardar cookie después de login
pm.sendRequest({
    url: pm.environment.get("baseUrl") + "/api/auth/login",
    method: 'POST',
    header: {
        'Content-Type': 'application/json',
    },
    body: {
        mode: 'raw',
        raw: JSON.stringify({
            username: "admin",
            password: "OldPassword123"
        })
    }
}, function (err, res) {
    if (!err && res.code === 200) {
        pm.collectionVariables.set("sessionCookie", res.headers.get("Set-Cookie"));
    }
});
```

### Request Headers
```
Content-Type: application/json
Cookie: {{sessionCookie}}
```

---

## 📝 Notas de Seguridad

1. **Sesiones:** Se usa cookie `JSESSIONID` para autenticación
2. **HTTPS:** En producción, siempre usar HTTPS para proteger credenciales
3. **CSRF:** Los endpoints `/api/**` tienen CSRF deshabilitado
4. **Rate Limiting:** Considerar implementar rate limiting para prevenir fuerza bruta
5. **Password Hash:** Se usa BCrypt con salt automático

---

## 🔍 Swagger UI

Acceder a la documentación interactiva (solo en desarrollo local):

```
http://localhost:8080/swagger-ui.html
```

Buscar el endpoint: **PUT /api/auth/change-password**
