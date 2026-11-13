ALTER TABLE cloud_class_task
    ADD COLUMN knowledge_point_id BIGINT NULL AFTER class_id;

ALTER TABLE cloud_class_task
    ADD CONSTRAINT fk_cloud_class_task_point
        FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point (id);

ALTER TABLE cloud_class_task
    CHANGE COLUMN point_cost reward_points INT NOT NULL DEFAULT 0;

ALTER TABLE cloud_class_submission
    ADD COLUMN learning_record_id BIGINT NULL AFTER student_id;

ALTER TABLE cloud_class_submission
    ADD CONSTRAINT fk_cloud_class_submission_record
        FOREIGN KEY (learning_record_id) REFERENCES learning_record (id);
