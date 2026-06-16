package com.blaie.blaie_be.core.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, List<String>> errors,
        String requestId
) {
    public static ApiErrorResponse of(ErrorCode errorCode, String message, Map<String, List<String>> errors, String requestId) {
        return new ApiErrorResponse(errorCode.name(), message, errors == null || errors.isEmpty() ? null : errors, requestId);
    }
}
