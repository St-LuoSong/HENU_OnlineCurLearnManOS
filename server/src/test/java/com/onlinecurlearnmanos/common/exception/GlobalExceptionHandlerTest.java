package com.onlinecurlearnmanos.common.exception;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 14-E 回归发现的缺陷：超过 multipart 上限的文件在进入控制器前就被容器拒绝，
 * 之前会以 500 返回，现在必须统一是 400 + 可读提示。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void oversizedUploadReturnsBadRequestInsteadOfServerError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.BAD_REQUEST.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(response.getBody().message()).contains("超过服务器允许的上传大小");
    }

    @Test
    void businessExceptionKeepsItsOwnStatusAndMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.CONFLICT, "该文件已被教学资源或作业引用")
        );

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.CONFLICT.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("已被教学资源或作业引用");
    }
}
