-- 论坛帖子图片：一条帖子最多 9 张，数量与用途由后端强校验，媒体顺序用于九宫格展示。
CREATE TABLE forum_post_media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    media_type VARCHAR(16) NOT NULL DEFAULT 'IMAGE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_post_media_file (post_id, file_id),
    KEY idx_forum_post_media_post (post_id, sort_order),
    KEY idx_forum_post_media_file (file_id),
    CONSTRAINT fk_forum_post_media_post FOREIGN KEY (post_id) REFERENCES forum_post (id),
    CONSTRAINT fk_forum_post_media_file FOREIGN KEY (file_id) REFERENCES file_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 教师重要公示：教师只能对自己负责的云班级范围发布，管理员可发布全局公示并治理。
CREATE TABLE forum_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    class_id BIGINT NULL,
    subject_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_forum_announcement_status (status, created_at),
    KEY idx_forum_announcement_author (author_id, created_at),
    KEY idx_forum_announcement_class (class_id),
    CONSTRAINT fk_forum_announcement_author FOREIGN KEY (author_id) REFERENCES sys_user (id),
    CONSTRAINT fk_forum_announcement_class FOREIGN KEY (class_id) REFERENCES cloud_class (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
