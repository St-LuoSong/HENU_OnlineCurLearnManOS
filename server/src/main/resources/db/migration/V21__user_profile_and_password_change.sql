ALTER TABLE sys_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER version;

CREATE TABLE user_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    avatar_url VARCHAR(512) NULL,
    department VARCHAR(128) NULL,
    major VARCHAR(128) NULL,
    grade_level VARCHAR(32) NULL,
    phone VARCHAR(32) NULL,
    bio VARCHAR(500) NULL,
    teacher_title VARCHAR(64) NULL,
    research_area VARCHAR(255) NULL,
    notify_email TINYINT NOT NULL DEFAULT 1,
    notify_forum_reply TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_user (user_id),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_change_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    new_password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewer_id BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    review_note VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_password_request_status (status, requested_at),
    KEY idx_password_request_user (user_id, status),
    CONSTRAINT fk_password_request_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_password_request_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
