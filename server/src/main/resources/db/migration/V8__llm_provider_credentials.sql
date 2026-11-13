ALTER TABLE llm_provider
    ADD COLUMN adapter_code VARCHAR(32) NOT NULL DEFAULT 'openai-compatible' AFTER code,
    ADD COLUMN encrypted_api_key TEXT NULL AFTER base_url,
    ADD COLUMN encryption_iv VARCHAR(64) NULL AFTER encrypted_api_key,
    ADD COLUMN key_version INT NOT NULL DEFAULT 1 AFTER encryption_iv,
    ADD COLUMN updated_by BIGINT NULL AFTER key_version;

ALTER TABLE llm_provider
    DROP COLUMN api_key_env;

UPDATE llm_provider
SET code = 'openai',
    name = 'OpenAI',
    adapter_code = 'openai-compatible'
WHERE code = 'openai-compatible';

INSERT INTO llm_provider (
    code,
    adapter_code,
    name,
    base_url,
    default_model,
    enabled,
    sort_order
)
VALUES
    ('deepseek', 'openai-compatible', 'DeepSeek', 'https://api.deepseek.com/v1', 'deepseek-chat', 1, 2),
    ('qwen', 'openai-compatible', '通义千问', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'qwen-plus', 1, 3),
    ('zhipu', 'openai-compatible', '智谱 GLM', 'https://open.bigmodel.cn/api/paas/v4', 'glm-4-flash', 1, 4);

ALTER TABLE llm_provider
    ADD CONSTRAINT fk_llm_provider_updated_by
        FOREIGN KEY (updated_by) REFERENCES sys_user (id);
