CREATE TABLE forum_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    detail VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    handler_id BIGINT NULL,
    handle_action VARCHAR(24) NULL,
    handle_note VARCHAR(1000) NULL,
    handled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_forum_report_status (status, created_at),
    KEY idx_forum_report_target (target_type, target_id),
    KEY idx_forum_report_reporter (reporter_id, created_at),
    CONSTRAINT fk_forum_report_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE forum_moderation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    report_id BIGINT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_summary VARCHAR(500) NULL,
    after_summary VARCHAR(500) NULL,
    patrol TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_forum_moderation_target (target_type, target_id, created_at),
    CONSTRAINT fk_forum_moderation_report FOREIGN KEY (report_id) REFERENCES forum_report (id),
    CONSTRAINT fk_forum_moderation_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
