package com.nona.api;

import com.nona.annotation.ScaffoldGenerated;

/**
 * 通用返回 response
 * <p>
 * 所有接口统一返回该结构。HTTP 语义下：
 * <ul>
 *     <li>成功响应 {@code code} 为固定成功码 {@value #SUCCESS}，以 {@code success=true} 标识</li>
 *     <li>失败响应由 {@code code} 承载业务码（小写点分，如 {@code generic.validation_failed}）</li>
 *     <li>HTTP 状态码由 {@code ExceptionAdviser} 集中设置，业务层与响应体不承载状态码</li>
 * </ul>
 *
 * @param code    成功为固定成功码，失败为业务码
 * @param message 提示信息
 * @param success 是否成功
 * @param data    业务数据（成功为业务数据，失败可为错误详情）
 * @param <T>     数据类型
 */
@ScaffoldGenerated
public record HttpResponse<T>(String code, String message, boolean success, T data) {

    /**
     * 成功码：成功响应 {@code code} 字段的固定取值（字段形态恒稳定，不随成功/失败变化）。
     */
    public static final String SUCCESS = "success";

    /**
     * 成功响应（无数据）。
     *
     * @return 成功响应体
     * @param <T> 数据类型
     */
    public static <T> HttpResponse<T> ok() {
        return new HttpResponse<>(SUCCESS, "success", true, null);
    }

    /**
     * 成功响应（携带数据）。
     *
     * @param data 业务数据
     * @return 成功响应体
     * @param <T> 数据类型
     */
    public static <T> HttpResponse<T> ok(T data) {
        return new HttpResponse<>(SUCCESS, "success", true, data);
    }

    /**
     * 失败响应（仅业务码 + 消息）。
     *
     * @param businessCode 业务码（小写点分，见 {@code BusinessCode}）
     * @param message      失败原因，展示给调用方
     * @return 失败响应体
     * @param <T> 数据类型
     */
    public static <T> HttpResponse<T> fail(String businessCode, String message) {
        return new HttpResponse<>(businessCode, message, false, null);
    }

    /**
     * 失败响应（业务码 + 消息 + 错误数据）。
     *
     * @param businessCode 业务码（小写点分，见 {@code BusinessCode}）
     * @param message      失败原因，展示给调用方
     * @param data         错误详情数据（如字段校验错误 map）
     * @return 失败响应体
     * @param <T> 数据类型
     */
    public static <T> HttpResponse<T> fail(String businessCode, String message, T data) {
        return new HttpResponse<>(businessCode, message, false, data);
    }
}
