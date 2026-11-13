CREATE TABLE llm_provider (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    api_key_env VARCHAR(64) NOT NULL,
    default_model VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_llm_provider_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE llm_call_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    response_content MEDIUMTEXT NULL,
    duration_ms BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_llm_call_idempotency (idempotency_key),
    KEY idx_llm_call_user (user_id, created_at),
    KEY idx_llm_call_status (status, created_at),
    CONSTRAINT fk_llm_call_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance INT NOT NULL DEFAULT 0,
    total_earned INT NOT NULL DEFAULT 0,
    total_spent INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_account_user (user_id),
    CONSTRAINT fk_point_account_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    balance_after INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    reference_type VARCHAR(32) NULL,
    reference_id VARCHAR(64) NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_transaction_idempotency (idempotency_key),
    KEY idx_point_transaction_user (user_id, created_at),
    KEY idx_point_transaction_status (status, created_at),
    CONSTRAINT fk_point_transaction_account FOREIGN KEY (account_id) REFERENCES point_account (id),
    CONSTRAINT fk_point_transaction_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO llm_provider (
    code,
    name,
    base_url,
    api_key_env,
    default_model,
    enabled,
    sort_order
)
VALUES (
    'openai-compatible',
    'OpenAI 兼容接口',
    'https://api.openai.com/v1',
    'OPENAI_API_KEY',
    'gpt-4o-mini',
    1,
    1
);
