package com.onlinecurlearnmanos.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_provider")
public class LlmProviderConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;

    private String adapterCode;

    private String name;

    private String baseUrl;

    private String encryptedApiKey;

    private String encryptionIv;

    private Integer keyVersion;

    private Long updatedBy;

    private String defaultModel;

    private Integer enabled;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
