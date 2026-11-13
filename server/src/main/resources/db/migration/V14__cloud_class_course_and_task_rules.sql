ALTER TABLE cloud_class
    ADD COLUMN subject_id BIGINT NULL AFTER teacher_id,
    ADD COLUMN academic_year VARCHAR(16) NULL AFTER class_code,
    ADD COLUMN semester VARCHAR(16) NULL AFTER academic_year,
    ADD COLUMN max_members INT NULL AFTER semester;

ALTER TABLE cloud_class
    ADD CONSTRAINT fk_cloud_class_subject FOREIGN KEY (subject_id) REFERENCES subject (id);

ALTER TABLE cloud_class
    ADD KEY idx_cloud_class_subject (subject_id, status);

ALTER TABLE cloud_class_task
    ADD COLUMN allow_resubmit TINYINT NOT NULL DEFAULT 0 AFTER point_cost,
    ADD COLUMN allow_late_submit TINYINT NOT NULL DEFAULT 0 AFTER allow_resubmit,
    ADD COLUMN max_score DECIMAL(5, 2) NULL AFTER allow_late_submit,
    ADD COLUMN published_at DATETIME(3) NULL AFTER max_score,
    ADD COLUMN closed_at DATETIME(3) NULL AFTER published_at;

UPDATE cloud_class_task
SET published_at = created_at
WHERE published_at IS NULL;

ALTER TABLE cloud_class_submission
    ADD COLUMN current_version INT NOT NULL DEFAULT 1 AFTER status;

CREATE TABLE cloud_class_submission_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content MEDIUMTEXT NULL,
    is_late TINYINT NOT NULL DEFAULT 0,
    score DECIMAL(5, 2) NULL,
    feedback VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    graded_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cloud_submission_version (submission_id, version_no),
    KEY idx_cloud_submission_version_task (task_id, student_id),
    CONSTRAINT fk_cloud_submission_version_submission
        FOREIGN KEY (submission_id) REFERENCES cloud_class_submission (id),
    CONSTRAINT fk_cloud_submission_version_task
        FOREIGN KEY (task_id) REFERENCES cloud_class_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO cloud_class_submission_version (
    submission_id,
    task_id,
    student_id,
    version_no,
    content,
    is_late,
    score,
    feedback,
    status,
    submitted_at,
    graded_at
)
SELECT
    id,
    task_id,
    student_id,
    1,
    content,
    0,
    score,
    feedback,
    status,
    submitted_at,
    graded_at
FROM cloud_class_submission;
