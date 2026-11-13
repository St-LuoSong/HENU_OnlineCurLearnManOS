CREATE TABLE file_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_key VARCHAR(255) NOT NULL,
    extension VARCHAR(16) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    storage_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    download_count INT NOT NULL DEFAULT 0,
    last_downloaded_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_asset_stored_key (stored_key),
    KEY idx_file_asset_owner (owner_id, created_at),
    KEY idx_file_asset_sha256 (sha256),
    CONSTRAINT fk_file_asset_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 平台教学资源附件（管理员配置的平台资源 + 教师自己发布的资源）
CREATE TABLE resource_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_attachment_file (resource_id, file_id),
    CONSTRAINT fk_resource_attachment_resource FOREIGN KEY (resource_id) REFERENCES learning_resource (id),
    CONSTRAINT fk_resource_attachment_file FOREIGN KEY (file_id) REFERENCES file_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 云班级教学资源附件（教师上传的课程文件）
CREATE TABLE cloud_resource_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_resource_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cloud_resource_attachment_file (class_resource_id, file_id),
    CONSTRAINT fk_cloud_resource_attachment_resource FOREIGN KEY (class_resource_id) REFERENCES cloud_class_resource (id),
    CONSTRAINT fk_cloud_resource_attachment_file FOREIGN KEY (file_id) REFERENCES file_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 学生云班级作业附件，保留每次提交的版本号
CREATE TABLE cloud_submission_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cloud_submission_attachment_file (submission_id, file_id),
    KEY idx_cloud_submission_attachment_version (submission_id, version_no),
    CONSTRAINT fk_cloud_submission_attachment_submission FOREIGN KEY (submission_id) REFERENCES cloud_class_submission (id),
    CONSTRAINT fk_cloud_submission_attachment_file FOREIGN KEY (file_id) REFERENCES file_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
