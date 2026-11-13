CREATE TABLE revoked_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    jti VARCHAR(64) NOT NULL,
    username VARCHAR(64) NULL,
    reason VARCHAR(32) NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_revoked_token_jti (jti),
    KEY idx_revoked_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
