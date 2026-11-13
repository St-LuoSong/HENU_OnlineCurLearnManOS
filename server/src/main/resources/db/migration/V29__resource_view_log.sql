CREATE TABLE resource_view_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    view_date DATE NOT NULL,
    viewed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_view_user_resource_date (user_id, resource_id, view_date),
    KEY idx_resource_view_user_date (user_id, view_date),
    CONSTRAINT fk_resource_view_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_resource_view_resource FOREIGN KEY (resource_id) REFERENCES learning_resource (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
