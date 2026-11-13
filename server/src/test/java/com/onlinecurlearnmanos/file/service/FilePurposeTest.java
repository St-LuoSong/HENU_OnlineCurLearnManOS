package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件用途矩阵（第四轮审计 16-D）：班级教学图片与作业图片各自独立用途，
 * 上传权限由服务端按用途判定，前端隐藏按钮不构成权限。
 */
class FilePurposeTest {

    @Test
    void imagePurposesUseIndependentImageValidation() {
        assertThat(FilePurpose.FORUM_IMAGE.isImageOnly()).isTrue();
        assertThat(FilePurpose.CLASS_RESOURCE_IMAGE.isImageOnly()).isTrue();
        assertThat(FilePurpose.SUBMISSION_IMAGE.isImageOnly()).isTrue();

        assertThat(FilePurpose.PLATFORM_RESOURCE.isImageOnly()).isFalse();
        assertThat(FilePurpose.CLASS_RESOURCE.isImageOnly()).isFalse();
        assertThat(FilePurpose.SUBMISSION.isImageOnly()).isFalse();
    }

    @Test
    void parsesImagePurposesCaseInsensitively() {
        assertThat(FilePurpose.parse("class_resource_image"))
                .isEqualTo(FilePurpose.CLASS_RESOURCE_IMAGE);
        assertThat(FilePurpose.parse(" SUBMISSION_IMAGE "))
                .isEqualTo(FilePurpose.SUBMISSION_IMAGE);
    }

    @Test
    void rejectsUnknownPurpose() {
        assertThatThrownBy(() -> FilePurpose.parse("CLASS_RESOURCE_VIDEO"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CLASS_RESOURCE_IMAGE");
    }

    @Test
    void studentCannotUploadClassResourceImage() {
        assertThatThrownBy(() -> FilePurpose.CLASS_RESOURCE_IMAGE.requireRole(List.of("STUDENT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有教师或管理员可以上传班级教学图片");
    }

    @Test
    void teacherCannotUploadSubmissionImage() {
        assertThatThrownBy(() -> FilePurpose.SUBMISSION_IMAGE.requireRole(List.of("TEACHER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有学生可以上传作业附件");
    }

    @Test
    void teacherUploadsClassResourceImageAndStudentUploadsSubmissionImage() {
        assertThatCode(() -> FilePurpose.CLASS_RESOURCE_IMAGE.requireRole(List.of("TEACHER")))
                .doesNotThrowAnyException();
        assertThatCode(() -> FilePurpose.CLASS_RESOURCE_IMAGE.requireRole(List.of("ADMIN")))
                .doesNotThrowAnyException();
        assertThatCode(() -> FilePurpose.SUBMISSION_IMAGE.requireRole(List.of("STUDENT")))
                .doesNotThrowAnyException();
    }

    @Test
    void studentsStillCannotUploadPlatformKnowledgeBaseFiles() {
        assertThatThrownBy(() -> FilePurpose.PLATFORM_RESOURCE.requireRole(List.of("STUDENT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有教师或管理员");
    }
}

