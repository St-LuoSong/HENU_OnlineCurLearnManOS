CREATE TABLE llm_model (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_id BIGINT NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    point_cost INT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_llm_model_provider_model (provider_id, model_code),
    KEY idx_llm_model_enabled (enabled, sort_order),
    CONSTRAINT fk_llm_model_provider FOREIGN KEY (provider_id) REFERENCES llm_provider (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_ai_preference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_ai_preference_user (user_id),
    CONSTRAINT fk_user_ai_preference_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_ai_preference_provider FOREIGN KEY (provider_id) REFERENCES llm_provider (id),
    CONSTRAINT fk_user_ai_preference_model FOREIGN KEY (model_id) REFERENCES llm_model (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'gpt-4o-mini', 'GPT-4o mini', 20, 1, 40
FROM llm_provider WHERE code = 'openai';

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'gpt-4o', 'GPT-4o', 40, 1, 50
FROM llm_provider WHERE code = 'openai';

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'deepseek-chat', 'DeepSeek Chat', 12, 1, 30
FROM llm_provider WHERE code = 'deepseek';

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'deepseek-reasoner', 'DeepSeek Reasoner', 25, 1, 45
FROM llm_provider WHERE code = 'deepseek';

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'qwen-plus', '通义千问 Plus', 8, 1, 20
FROM llm_provider WHERE code = 'qwen';

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'qwen-max', '通义千问 Max', 18, 1, 35
FROM llm_provider WHERE code = 'qwen';

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'glm-4-flash', '智谱 GLM-4 Flash', 5, 1, 10
FROM llm_provider WHERE code = 'zhipu';

INSERT INTO llm_model (
    provider_id,
    model_code,
    display_name,
    point_cost,
    enabled,
    sort_order
)
SELECT id, 'glm-4-plus', '智谱 GLM-4 Plus', 15, 1, 25
FROM llm_provider WHERE code = 'zhipu';
