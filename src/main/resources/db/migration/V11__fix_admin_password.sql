-- Fix admin user password and ensure enabled
-- This migration ensures the admin user has the correct password and is enabled
-- Safe to run multiple times (idempotent)

-- Update admin user to have correct password and be enabled
UPDATE users 
SET password = '$2a$10$Gzs9U5CRc5uVVtQc5D0vEu.hhzpPLIDh3TP1cFVHkHJ.Kq/e3FFGW',
    enabled = true,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND (password != '$2a$10$Gzs9U5CRc5uVVtQc5D0vEu.hhzpPLIDh3TP1cFVHkHJ.Kq/e3FFGW' 
       OR enabled != true);

-- Log the fix
-- The admin user now has password 'changeme' and is enabled
