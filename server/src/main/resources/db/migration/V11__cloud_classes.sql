CREATE TABLE cloud_class (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    teacher_id BIGINT NOT NULL,
    class_code VARCHAR(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cloud_class_code (class_code),
    KEY idx_cloud_class_teacher (teacher_id, updated_at),
    CONSTRAINT fk_cloud_class_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cloud_class_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cloud_class_member (class_id, student_id),
    KEY idx_cloud_class_member_student (student_id, joined_at),
    CONSTRAINT fk_cloud_class_member_class FOREIGN KEY (class_id) REFERENCES cloud_class (id),
    CONSTRAINT fk_cloud_class_member_student FOREIGN KEY (student_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cloud_class_resource (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    publisher_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    content MEDIUMTEXT NULL,
    external_url VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cloud_class_resource_class (class_id, created_at),
    CONSTRAINT fk_cloud_class_resource_class FOREIGN KEY (class_id) REFERENCES cloud_class (id),
    CONSTRAINT fk_cloud_class_resource_publisher FOREIGN KEY (publisher_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cloud_class_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    task_type VARCHAR(16) NOT NULL DEFAULT 'TASK',
    due_at DATETIME(3) NULL,
    point_cost INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cloud_class_task_class (class_id, created_at),
    CONSTRAINT fk_cloud_class_task_class FOREIGN KEY (class_id) REFERENCES cloud_class (id),
    CONSTRAINT fk_cloud_class_task_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cloud_class_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    content MEDIUMTEXT NULL,
    score DECIMAL(5, 2) NULL,
    feedback VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    graded_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cloud_class_submission (task_id, student_id),
    KEY idx_cloud_class_submission_student (student_id, submitted_at),
    CONSTRAINT fk_cloud_class_submission_task FOREIGN KEY (task_id) REFERENCES cloud_class_task (id),
    CONSTRAINT fk_cloud_class_submission_student FOREIGN KEY (student_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
