package com.iaaops.shared.error;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String detail;
    private final java.util.List<ErrorResponse.FieldError> errors;

    public ApiException(ErrorCode code, String title) {
        this(code, title, null);
    }

    public ApiException(ErrorCode code, String title, String detail) {
        this(code, title, detail, java.util.List.of());
    }

    public ApiException(ErrorCode code, String title, String detail,
            java.util.List<ErrorResponse.FieldError> errors) {
        super(title);
        this.code = code;
        this.detail = detail;
        this.errors = errors;
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    public java.util.List<ErrorResponse.FieldError> errors() {
        return errors;
    }

    /** 请求体整体不成立（跨字段约束），字段名统一记为 body，与既有实现一致。 */
    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "请求参数校验失败", null,
                java.util.List.of(new ErrorResponse.FieldError("body", message)));
    }

    public static ApiException unauthorized(String title) {
        return new ApiException(ErrorCode.AUTH_REQUIRED, title);
    }

    public static ApiException forbidden(String title, String detail) {
        return new ApiException(ErrorCode.FORBIDDEN, title, detail);
    }

    public static ApiException notFound(String title) {
        return new ApiException(ErrorCode.NOT_FOUND, title);
    }

    public static ApiException conflict(String title, String detail) {
        return new ApiException(ErrorCode.CONFLICT, title, detail);
    }
}
