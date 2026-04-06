-- Agregar columna password_change_required a tabla users
-- Para implementar flujo de cambio de contraseña obligatorio en primer login

ALTER TABLE users
ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT false;

-- Los usuarios existentes no requieren cambio de contraseña (DEFAULT false)
-- Los usuarios nuevos se crearán con este flag en true desde UserService.createUser()
