package com.community.medrecycle.common;

/** 业务异常：code 见 GlobalExceptionHandler 约定 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(40000, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
