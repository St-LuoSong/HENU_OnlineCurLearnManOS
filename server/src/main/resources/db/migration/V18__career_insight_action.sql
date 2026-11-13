ALTER TABLE career_analysis_insight
    ADD COLUMN reason VARCHAR(1000) NULL AFTER content,
    ADD COLUMN target_date DATE NULL AFTER reason,
    ADD COLUMN converted_goal_id BIGINT NULL AFTER target_date,
    ADD COLUMN converted_task_id BIGINT NULL AFTER converted_goal_id;

ALTER TABLE career_analysis_insight
    ADD CONSTRAINT fk_career_insight_goal
        FOREIGN KEY (converted_goal_id) REFERENCES learning_goal (id);

ALTER TABLE career_analysis_insight
    ADD CONSTRAINT fk_career_insight_task
        FOREIGN KEY (converted_task_id) REFERENCES learning_task (id);
