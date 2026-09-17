package com.iaaops.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 所有 4xx/5xx 统一返回 application/problem+json，格式与现有契约一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException exception) {
        return problem(exception.code(), exception.getMessage(), exception.detail(), exception.errors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(snakeCase(error.getField()), error.getDefaultMessage()))
                .toList();
        return problem(ErrorCode.VALIDATION_FAILED, "请求参数校验失败", null, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return problem(ErrorCode.VALIDATION_FAILED, "请求体无法解析", exception.getMostSpecificCause().getMessage(), null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception) {
        return problem(ErrorCode.PAYLOAD_TOO_LARGE, "上传文件超过大小限制", "单个文件上限 10MB", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissing(NoResourceFoundException exception) {
        return problem(ErrorCode.NOT_FOUND, "路径不存在", exception.getResourcePath(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException exception) {
        return problem(ErrorCode.METHOD_NOT_ALLOWED, "不支持该 HTTP 方法", exception.getMethod(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        // 未预期异常不把堆栈或内部信息透给调用方，只给一个可追查的标识。
        log.error("未处理异常 path={}", request.getRequestURI(), exception);
        return problem(ErrorCode.INTERNAL_ERROR, "服务端异常", null, null);
    }

    /** 校验错误里的字段名按对外契约给 snake_case，与请求体字段一致。 */
    private static String snakeCase(String property) {
        StringBuilder builder = new StringBuilder(property.length() + 4);
        for (char character : property.toCharArray()) {
            if (Character.isUpperCase(character)) {
                builder.append('_').append(Character.toLowerCase(character));
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private ResponseEntity<ErrorResponse> problem(ErrorCode code, String title, String detail,
            List<ErrorResponse.FieldError> errors) {
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(ErrorResponse.of(code, title, detail, errors));
    }
}
