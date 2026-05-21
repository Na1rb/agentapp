package com.nairb.ai130.common.exception;

/**
 * 业务异常。由 {@link GlobalExceptionHandler} 统一捕获并转换为 {@code ApiResponse}。
 * <p>
 * 用法：
 * <pre>{@code
 * throw new BusinessException(400, "文件名不能为空");
 * throw new BusinessException("不支持的文件格式");  // 默认 code=400
 * }</pre>
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 默认 code = 400（客户端请求错误） */
    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
