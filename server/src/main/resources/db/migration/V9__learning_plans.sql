CREATE TABLE learning_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    provider_code VARCHAR(32) NULL,
    model VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_learning_plan_user (user_id, updated_at),
    CONSTRAINT fk_learning_plan_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_plan_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    resource_id BIGINT NULL,
    knowledge_point_id BIGINT NULL,
    task_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'TODO',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_learning_plan_item_plan (plan_id, sort_order),
    CONSTRAINT fk_learning_plan_item_plan
        FOREIGN KEY (plan_id) REFERENCES learning_plan (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_learning_plan_item_resource
        FOREIGN KEY (resource_id) REFERENCES learning_resource (id),
    CONSTRAINT fk_learning_plan_item_point
        FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point (id),
    CONSTRAINT fk_learning_plan_item_task
        FOREIGN KEY (task_id) REFERENCES learning_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
