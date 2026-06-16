package com.blaie.blaie_be.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        String message,
        PageMeta meta
) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(data, message, null);
    }

    public static <T> ApiResponse<T> of(T data, String message, PageMeta meta) {
        return new ApiResponse<>(data, message, meta);
    }
}
