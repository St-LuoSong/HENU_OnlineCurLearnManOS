ALTER TABLE sys_user
    ADD COLUMN account_deleted_at DATETIME(3) NULL AFTER token_version,
    ADD COLUMN account_deleted_by BIGINT NULL AFTER account_deleted_at,
    ADD COLUMN account_delete_reason VARCHAR(500) NULL AFTER account_deleted_by;

CREATE INDEX idx_sys_user_account_deleted ON sys_user (deleted, account_deleted_at);
