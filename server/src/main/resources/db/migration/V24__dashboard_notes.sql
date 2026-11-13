-- 学习概览的日历便签：只归属创建者，与任务 / 目标状态完全解耦，删除走逻辑删除。
CREATE TABLE dashboard_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    note_date DATE NOT NULL,
    content VARCHAR(500) NOT NULL,
    tone VARCHAR(16) NOT NULL DEFAULT 'DEFAULT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dashboard_note_user_date (user_id, note_date),
    CONSTRAINT fk_dashboard_note_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

