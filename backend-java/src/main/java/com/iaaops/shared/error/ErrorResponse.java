package com.iaaops.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * RFC 9457 Problem Details，字段与 contracts/openapi.json 的 Error 模型逐字对应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String type,
        String title,
        int status,
        String code,
        String detail,
        List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String title, String detail, List<FieldError> errors) {
        return new ErrorResponse("about:blank", title, code.status().value(), code.name(), detail,
                errors == null || errors.isEmpty() ? null : errors);
    }
}
