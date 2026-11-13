CREATE TABLE career_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    consent_granted TINYINT NOT NULL DEFAULT 0,
    consent_version VARCHAR(16) NOT NULL DEFAULT 'v1',
    consented_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    include_learning_records TINYINT NOT NULL DEFAULT 1,
    include_goals TINYINT NOT NULL DEFAULT 1,
    include_grades TINYINT NOT NULL DEFAULT 1,
    major VARCHAR(128) NULL,
    grade_level VARCHAR(32) NULL,
    weekly_hours INT NULL,
    interest_tags VARCHAR(500) NULL,
    strength_notes VARCHAR(1000) NULL,
    career_goal VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_career_profile_user (user_id),
    CONSTRAINT fk_career_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE career_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    provider_code VARCHAR(32) NULL,
    model VARCHAR(128) NULL,
    data_sources VARCHAR(255) NULL,
    data_snapshot TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_career_analysis_user (user_id, created_at),
    CONSTRAINT fk_career_analysis_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE career_analysis_insight (
    id BIGINT NOT NULL AUTO_INCREMENT,
    analysis_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(2000) NULL,
    score INT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_career_insight_analysis (analysis_id, category, sort_order),
    CONSTRAINT fk_career_insight_analysis
        FOREIGN KEY (analysis_id) REFERENCES career_analysis (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
