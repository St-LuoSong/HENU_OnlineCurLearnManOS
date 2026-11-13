ALTER TABLE learning_plan_item
    ADD COLUMN goal_id BIGINT NULL AFTER knowledge_point_id;

ALTER TABLE learning_plan_item
    ADD CONSTRAINT fk_learning_plan_item_goal
        FOREIGN KEY (goal_id) REFERENCES learning_goal (id);
