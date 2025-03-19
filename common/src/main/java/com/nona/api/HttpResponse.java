package com.nona.api;

/**
 * 通用返回response
 *
 * @param <T> 数据类型
 */
public record HttpResponse<T>(int code, String message, boolean success, T data) {

    public static final int SUCCESS_CODE = 0;
    public static final int UNAUTHORIZED_CODE = 401;
    public static final int FAIL_CODE = 500;

    public static <T> HttpResponse<T> ok() {
        return new HttpResponse<>(SUCCESS_CODE, "success", true, null);
    }

    public static <T> HttpResponse<T> ok(T data) {
        return new HttpResponse<>(SUCCESS_CODE, "success", true, data);
    }

    public static <T> HttpResponse<T> fail(String message) {
        return new HttpResponse<>(FAIL_CODE, message, false, null);
    }

    public static <T> HttpResponse<T> fail(T data) {
        return new HttpResponse<>(FAIL_CODE, "fail", false, data);
    }

    public static <T> HttpResponse<T> fail(String message, T data) {
        return new HttpResponse<>(FAIL_CODE, message, false, data);
    }

    public static <T> HttpResponse<T> unauthorizedFail() {
        return new HttpResponse<>(UNAUTHORIZED_CODE, "unauthorized", false, null);
    }
}
