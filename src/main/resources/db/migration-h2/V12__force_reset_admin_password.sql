-- H2 version - Force reset admin password (no conditions)
-- This ensures the admin user has the correct password regardless of current state

-- First, ensure the admin user exists (insert if not exists)
MERGE INTO users (username, password, full_name, email, enabled, role, created_at, updated_at)
KEY(username)
VALUES ('admin', '$2a$10$Gzs9U5CRc5uVVtQc5D0vEu.hhzpPLIDh3TP1cFVHkHJ.Kq/e3FFGW', 'Administrador', 'admin@bunnycure.cl', TRUE, 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Then, unconditionally update the admin user to have correct password and be enabled
UPDATE users 
SET password = '$2a$10$Gzs9U5CRc5uVVtQc5D0vEu.hhzpPLIDh3TP1cFVHkHJ.Kq/e3FFGW',
    enabled = true,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin';
