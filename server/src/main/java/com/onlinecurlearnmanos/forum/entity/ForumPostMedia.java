package com.onlinecurlearnmanos.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛帖子与文件资产的媒体关系。
 *
 * <p>只保存关系与排序，不保存物理路径；图片数量、用途与归属由服务层强校验，
 * 因此删除关系不会删除文件，孤儿文件统一交给管理端清理任务处理。</p>
 *
 * <p>关系表采用物理删除（与资源附件一致）：逻辑删除会与 (post_id, file_id) 唯一键冲突，
 * 导致「移除后重新添加同一张图片」直接失败。</p>
 */
@Data
@TableName("forum_post_media")
public class ForumPostMedia {

    public static final String TYPE_IMAGE = "IMAGE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long fileId;

    private String mediaType;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
