CREATE TABLE growth_cycle (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    completed_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN user_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_growth_cycle_active_user (active_user_id),
    KEY idx_growth_cycle_user (user_id, start_date),
    CONSTRAINT fk_growth_cycle_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE growth_cycle_direction (
    cycle_id BIGINT NOT NULL,
    source_analysis_id BIGINT NULL,
    direction_summary MEDIUMTEXT NULL,
    snapshot_json JSON NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (cycle_id),
    CONSTRAINT fk_growth_direction_cycle FOREIGN KEY (cycle_id) REFERENCES growth_cycle (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE growth_weekly_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cycle_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    evidence_json JSON NULL,
    note VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_growth_review_cycle_week (cycle_id, week_start),
    CONSTRAINT fk_growth_review_cycle FOREIGN KEY (cycle_id) REFERENCES growth_cycle (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE learning_goal
    ADD COLUMN cycle_id BIGINT NULL AFTER user_id,
    ADD COLUMN source_goal_id BIGINT NULL AFTER cycle_id,
    ADD KEY idx_learning_goal_cycle (cycle_id, status),
    ADD CONSTRAINT fk_learning_goal_cycle FOREIGN KEY (cycle_id) REFERENCES growth_cycle (id);

ALTER TABLE learning_task
    ADD COLUMN cycle_id BIGINT NULL AFTER user_id,
    ADD COLUMN source_task_id BIGINT NULL AFTER cycle_id,
    ADD KEY idx_learning_task_cycle (cycle_id, status),
    ADD CONSTRAINT fk_learning_task_cycle FOREIGN KEY (cycle_id) REFERENCES growth_cycle (id);

ALTER TABLE learning_plan
    ADD COLUMN cycle_id BIGINT NULL AFTER user_id,
    ADD KEY idx_learning_plan_cycle (cycle_id, status),
    ADD CONSTRAINT fk_learning_plan_cycle FOREIGN KEY (cycle_id) REFERENCES growth_cycle (id);
