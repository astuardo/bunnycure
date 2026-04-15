-- Corregir tipos de datos para PostgreSQL en Heroku
ALTER TABLE loyalty_rewards ALTER COLUMN id TYPE BIGINT;
ALTER TABLE loyalty_reward_history ALTER COLUMN id TYPE BIGINT;
