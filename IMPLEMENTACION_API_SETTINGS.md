# API REST de Configuración del Sistema - Implementación Completada

## 📁 Archivos Creados

### DTOs (`src/main/java/cl/bunnycure/web/dto/`)

1. **AppSettingsDto.java** - DTO principal para devolver todas las configuraciones agrupadas por secciones:
   - `BrandingSettings`: Identidad del negocio (nombre, slogan, colores, etc.)
   - `WhatsAppSettings`: Configuración de WhatsApp
   - `BookingSettings`: Configuración de reservas y bloques horarios
   - `ReminderSettings`: Estrategia de recordatorios
   - `FieldSettings`: Modos de campos dinámicos (REQUIRED, OPTIONAL, HIDDEN)

2. **UpdateSettingRequest.java** - DTO para actualizar una configuración individual
   - Campo: `value` (validado como no vacío)

3. **BulkUpdateSettingsRequest.java** - DTO para actualizar múltiples configuraciones
   - Campo: `settings` (Map<String, String>, no puede estar vacío)

### Controller (`src/main/java/cl/bunnycure/web/controller/`)

**SettingsApiController.java** - API REST para configuración del sistema

## 🔌 Endpoints Implementados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/settings` | Obtener todas las configuraciones agrupadas |
| `GET` | `/api/settings/{key}` | Obtener una configuración específica |
| `PUT` | `/api/settings/{key}` | Actualizar una configuración |
| `PUT` | `/api/settings/bulk` | Actualizar múltiples configuraciones |
| `POST` | `/api/settings/reset` | Resetear a valores por defecto |

## 📝 Claves de Configuración Soportadas

### Branding & Identidad
- `app.name` - Nombre del negocio
- `app.slogan` - Eslogan
- `app.email` - Email de contacto
- `app.logo-url` - URL del logo
- `app.primary-color` - Color primario (formato #RRGGBB)
- `app.secondary-color` - Color secundario (formato #RRGGBB)
- `app.timezone` - Zona horaria
- `app.locale` - Locale (ej: es_CL)
- `app.currency` - Moneda (ej: CLP)
- `app.service-tip` - Consejo de servicio

### WhatsApp
- `whatsapp.number` - Número principal
- `whatsapp.human.number` - Número de atención humana
- `whatsapp.admin-alert.number` - Número para alertas admin
- `whatsapp.human.display-name` - Nombre de display
- `whatsapp.handoff.enabled` - Handoff habilitado (true/false)
- `whatsapp.handoff.client-message` - Mensaje al cliente
- `whatsapp.handoff.admin-prefill` - Prefill para admin

### Booking (Reservas)
- `booking.enabled` - Sistema de reservas habilitado (true/false)
- `booking.message.template` - Template del mensaje
- `booking.block.morning` - Rango horario mañana
- `booking.block.afternoon` - Rango horario tarde
- `booking.block.night` - Rango horario noche
- `booking.block.morning.enabled` - Bloque mañana habilitado (true/false)
- `booking.block.afternoon.enabled` - Bloque tarde habilitado (true/false)
- `booking.block.night.enabled` - Bloque noche habilitado (true/false)

### Reminders (Recordatorios)
- `reminder.strategy` - Estrategia de recordatorios
  - Valores válidos: `2hours`, `morning`, `day_before`, `both`

### Field Modes (Campos Dinámicos)
- `field.email.mode` - Modo del campo email
- `field.gender.mode` - Modo del campo género
- `field.birth-date.mode` - Modo del campo fecha de nacimiento
- `field.emergency-phone.mode` - Modo del campo teléfono emergencia
- `field.health-notes.mode` - Modo del campo notas de salud
- `field.general-notes.mode` - Modo del campo notas generales
  - Valores válidos: `REQUIRED`, `OPTIONAL`, `HIDDEN`

## 🔒 Seguridad

- **Rol requerido**: `ADMIN` (configurado en `SecurityConfig.java`)
- **Endpoint protegido**: `/api/settings/**`
- Todos los endpoints requieren autenticación como ADMIN

## ✅ Validaciones Implementadas

1. **Validación de claves**: Solo se permiten claves predefinidas en `VALID_KEYS`
2. **Validación de valores**:
   - Estrategias de recordatorio: Solo valores del enum permitido
   - Modos de campo: Solo `REQUIRED`, `OPTIONAL`, `HIDDEN`
   - Booleanos: Solo `true` o `false`
   - Colores: Formato hexadecimal `#RRGGBB`
3. **Logging**: Todas las operaciones se registran en los logs

## 📊 Estructura de Respuesta

Todas las respuestas usan el patrón `ApiResponse<T>`:

```json
{
  "success": true,
  "data": { ... },
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
    "message": "Clave de configuración no válida",
    "errorCode": "INVALID_KEY"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

## 📖 Ejemplos de Uso

### 1. Obtener todas las configuraciones

```bash
GET /api/settings

Response:
{
  "success": true,
  "data": {
    "branding": {
      "name": "BunnyCure",
      "slogan": "Arte en tus manos ✨",
      "email": "contacto@bunnycure.cl",
      ...
    },
    "whatsapp": {
      "number": "56988873031",
      "humanDisplayName": "Equipo BunnyCure",
      ...
    },
    "booking": {
      "enabled": true,
      "messageTemplate": "...",
      "morningBlock": {
        "timeRange": "09:00 – 13:00",
        "enabled": true
      },
      ...
    },
    "reminders": {
      "strategy": "2hours"
    },
    "fields": {
      "emailMode": "OPTIONAL",
      "genderMode": "OPTIONAL",
      ...
    }
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

### 2. Obtener una configuración específica

```bash
GET /api/settings/app.name

Response:
{
  "success": true,
  "data": {
    "key": "app.name",
    "value": "BunnyCure"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

### 3. Actualizar una configuración

```bash
PUT /api/settings/app.name
{
  "value": "Mi Negocio"
}

Response:
{
  "success": true,
  "data": {
    "key": "app.name",
    "value": "Mi Negocio"
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

### 4. Actualizar múltiples configuraciones

```bash
PUT /api/settings/bulk
{
  "settings": {
    "app.name": "BunnyCure Actualizado",
    "app.primary-color": "#FF0000",
    "booking.enabled": "false"
  }
}

Response:
{
  "success": true,
  "data": {
    "updated": 3,
    "keys": ["app.name", "app.primary-color", "booking.enabled"]
  },
  "timestamp": "2024-01-15T10:30:00"
}
```

### 5. Resetear a valores por defecto

```bash
POST /api/settings/reset

Response:
{
  "success": true,
  "data": "Configuraciones reseteadas a valores por defecto",
  "timestamp": "2024-01-15T10:30:00"
}
```

## 🎯 Códigos de Error

| Código | Descripción |
|--------|-------------|
| `INVALID_KEY` | Clave de configuración no válida |
| `INVALID_VALUE` | Valor inválido para la configuración |
| `INVALID_KEYS` | Una o más claves son inválidas (bulk update) |
| `VALIDATION_ERRORS` | Errores de validación en múltiples valores |

## ✨ Características Destacadas

1. **Documentación Swagger**: Todos los endpoints están documentados con anotaciones de OpenAPI
2. **Validaciones exhaustivas**: Cada tipo de configuración tiene validaciones específicas
3. **Logging completo**: Todas las operaciones se registran para auditoría
4. **Respuestas consistentes**: Uso del patrón `ApiResponse<T>` en toda la API
5. **Seguridad robusta**: Solo usuarios ADMIN pueden acceder
6. **Estructura agrupada**: Las configuraciones se devuelven organizadas por secciones lógicas

## 🔧 Archivos Modificados

- `SecurityConfig.java`: Agregada línea 73 para proteger `/api/settings/**` con rol ADMIN

## ✅ Testing Recomendado

1. Probar cada endpoint con Postman o herramienta similar
2. Verificar validaciones con valores incorrectos
3. Probar autenticación (debe fallar sin token de ADMIN)
4. Verificar logging en consola
5. Probar reseteo y verificar que los valores vuelvan a defaults

---

**Implementación completada exitosamente** ✅
