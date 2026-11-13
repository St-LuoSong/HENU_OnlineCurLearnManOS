CREATE TABLE knowledge_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    subject_id BIGINT NULL,
    knowledge_point_id BIGINT NULL,
    question MEDIUMTEXT NULL,
    answer MEDIUMTEXT NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_favorite_user_message (user_id, message_id),
    KEY idx_knowledge_favorite_user (user_id, created_at),
    KEY idx_knowledge_favorite_subject (user_id, subject_id),
    CONSTRAINT fk_knowledge_favorite_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
