package com.onlinecurlearnmanos.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_user")
public class SysUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String displayName;

    private String email;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;

    /** 会话版本：管理员批准密码变更后自增，使旧 token 立即失效。 */
    private Integer tokenVersion;

    /** 审计型逻辑删除信息：账号删除时间、操作人、删除原因。 */
    private LocalDateTime accountDeletedAt;

    private Long accountDeletedBy;

    private String accountDeleteReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Integer tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public LocalDateTime getAccountDeletedAt() {
        return accountDeletedAt;
    }

    public void setAccountDeletedAt(LocalDateTime accountDeletedAt) {
        this.accountDeletedAt = accountDeletedAt;
    }

    public Long getAccountDeletedBy() {
        return accountDeletedBy;
    }

    public void setAccountDeletedBy(Long accountDeletedBy) {
        this.accountDeletedBy = accountDeletedBy;
    }

    public String getAccountDeleteReason() {
        return accountDeleteReason;
    }

    public void setAccountDeleteReason(String accountDeleteReason) {
        this.accountDeleteReason = accountDeleteReason;
    }
}
