-- Agregar columna password_change_required a tabla users
-- Para implementar flujo de cambio de contraseña obligatorio en primer login

ALTER TABLE users
ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT false;

-- Comentario explicativo
COMMENT ON COLUMN users.password_change_required IS 'Indica si el usuario debe cambiar su contraseña en el próximo login';

-- Los usuarios existentes no requieren cambio de contraseña (DEFAULT false)
-- Los usuarios nuevos se crearán con este flag en true desde UserService.createUser()
