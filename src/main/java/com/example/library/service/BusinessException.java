package com.example.library.service;

/** 业务规则校验失败，消息可直接展示给用户 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
