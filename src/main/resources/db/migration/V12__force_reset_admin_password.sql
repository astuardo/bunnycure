-- V12: Force reset admin password (no conditions)
-- This ensures the admin user has the correct password regardless of current state
-- This migration is unconditional to guarantee the fix works

-- First, ensure the admin user exists (insert if not exists)
INSERT INTO users (username, password, full_name, email, enabled, role, created_at, updated_at)
SELECT 'admin', '$2a$10$Gzs9U5CRc5uVVtQc5D0vEu.hhzpPLIDh3TP1cFVHkHJ.Kq/e3FFGW', 'Administrador', 'admin@bunnycure.cl', TRUE, 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- Then, unconditionally update the admin user to have correct password and be enabled
UPDATE users 
SET password = '$2a$10$Gzs9U5CRc5uVVtQc5D0vEu.hhzpPLIDh3TP1cFVHkHJ.Kq/e3FFGW',
    enabled = true,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin';

-- Verify the admin user is properly configured
-- The user should now be able to login with password 'changeme'
