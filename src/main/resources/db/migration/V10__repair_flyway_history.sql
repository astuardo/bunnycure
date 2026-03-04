-- Repair Flyway schema history for failed migrations
-- This migration fixes the Flyway schema history table when migrations have been applied
-- but marked as failed due to the database already having the tables/columns
-- This is safe to run multiple times (idempotent)

-- Update failed V2 migration to success if service_catalog table exists
UPDATE flyway_schema_history
SET success = true, execution_time = 0
WHERE version = '2' AND success = false
  AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'service_catalog')
  AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '2');

-- Update failed V3 migration to success if reminder_sent column exists
UPDATE flyway_schema_history
SET success = true, execution_time = 0
WHERE version = '3' AND success = false
  AND EXISTS (SELECT 1 FROM information_schema.columns 
              WHERE table_name = 'appointments' AND column_name = 'reminder_sent')
  AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '3');

-- Update failed V5 migration to success if users table exists
UPDATE flyway_schema_history
SET success = true, execution_time = 0
WHERE version = '5' AND success = false
  AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users')
  AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '5');

-- Update failed V6 migration to success if app_settings table exists
UPDATE flyway_schema_history
SET success = true, execution_time = 0
WHERE version = '6' AND success = false
  AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'app_settings')
  AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '6');

-- Update failed V7 migration to success if booking_requests table exists
UPDATE flyway_schema_history
SET success = true, execution_time = 0
WHERE version = '7' AND success = false
  AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'booking_requests')
  AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '7');
