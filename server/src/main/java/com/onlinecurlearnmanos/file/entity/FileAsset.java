package com.onlinecurlearnmanos.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一文件资产。业务表只引用文件 ID，不保存磁盘路径，便于后续切换对象存储。
 */
@Data
@TableName("file_asset")
public class FileAsset {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String originalName;

    private String storedKey;

    private String extension;

    private String mimeType;

    private Long sizeBytes;

    private String sha256;

    private String purpose;

    private String storageProvider;

    private String status;

    private Integer downloadCount;

    private LocalDateTime lastDownloadedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
