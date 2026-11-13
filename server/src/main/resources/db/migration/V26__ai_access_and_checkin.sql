ALTER TABLE llm_model
    ADD COLUMN tier VARCHAR(16) NOT NULL DEFAULT 'STANDARD' AFTER display_name,
    ADD KEY idx_llm_model_tier (tier, enabled, sort_order);

UPDATE llm_model SET tier = 'EXPERT'
WHERE model_code IN ('gpt-4o', 'deepseek-reasoner', 'qwen-max', 'glm-4-plus');

ALTER TABLE llm_call_record
    ADD COLUMN access_mode VARCHAR(16) NOT NULL DEFAULT 'EXPERT' AFTER model,
    ADD KEY idx_llm_call_mode (user_id, access_mode, created_at);

ALTER TABLE chat_session
    ADD COLUMN topic VARCHAR(255) NULL AFTER title;

CREATE TABLE ai_daily_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    standard_used INT NOT NULL DEFAULT 0,
    expert_used INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_daily_usage_user_date (user_id, usage_date),
    CONSTRAINT fk_ai_daily_usage_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE daily_checkin (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    streak_days INT NOT NULL,
    reward_points INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_checkin_user_date (user_id, checkin_date),
    KEY idx_daily_checkin_user_date (user_id, checkin_date),
    CONSTRAINT fk_daily_checkin_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
