-- 论坛消息区只保存用户对回复 / 教师提醒的“已了解”状态，消息内容实时从业务表派生。
CREATE TABLE forum_message_ack (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(16) NOT NULL,
    source_id BIGINT NOT NULL,
    acknowledged_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_message_ack_user_source (user_id, message_type, source_id),
    KEY idx_forum_message_ack_user_time (user_id, acknowledged_at),
    CONSTRAINT fk_forum_message_ack_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
