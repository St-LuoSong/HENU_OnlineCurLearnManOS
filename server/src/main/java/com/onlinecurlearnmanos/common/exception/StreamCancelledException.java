package com.onlinecurlearnmanos.common.exception;

/**
 * 客户端已停止生成。与普通失败区分，调用方据此跳过失败标记写入。
 */
public class StreamCancelledException extends RuntimeException {

    public StreamCancelledException(String message) {
        super(message);
    }
}
