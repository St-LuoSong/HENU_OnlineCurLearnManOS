ALTER TABLE cloud_class
    ADD COLUMN join_enabled TINYINT NOT NULL DEFAULT 1 AFTER status;
