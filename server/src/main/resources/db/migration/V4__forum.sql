CREATE TABLE forum_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE forum_post (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    reply_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_forum_post_category (category_id, created_at),
    KEY idx_forum_post_author (author_id, created_at),
    CONSTRAINT fk_forum_post_category FOREIGN KEY (category_id) REFERENCES forum_category (id),
    CONSTRAINT fk_forum_post_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE forum_reply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_forum_reply_post (post_id, created_at),
    KEY idx_forum_reply_author (author_id, created_at),
    CONSTRAINT fk_forum_reply_post FOREIGN KEY (post_id) REFERENCES forum_post (id),
    CONSTRAINT fk_forum_reply_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE forum_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_forum_like_user_post (user_id, post_id),
    CONSTRAINT fk_forum_like_post
        FOREIGN KEY (post_id) REFERENCES forum_post (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_forum_like_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO forum_category (id, code, name, description, sort_order)
VALUES
    (1, 'GENERAL', '学习交流', '学习方法、经验与日常交流', 1),
    (2, 'RESOURCE', '资源求助', '寻找资料、提问资源相关问题', 2),
    (3, 'COURSE', '课程讨论', '围绕课程内容和知识点展开讨论', 3),
    (4, 'NOTICE', '教师公告', '教师发布的通知与说明', 4),
    (5, 'OTHER', '其他', '不属于以上分类的内容', 5);
