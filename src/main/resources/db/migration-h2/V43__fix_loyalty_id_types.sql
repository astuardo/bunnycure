-- H2 compatibility for loyalty ID types
ALTER TABLE loyalty_rewards ALTER COLUMN id BIGINT;
ALTER TABLE loyalty_reward_history ALTER COLUMN id BIGINT;
