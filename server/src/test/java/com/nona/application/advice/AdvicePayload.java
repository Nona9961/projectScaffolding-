package com.nona.application.advice;

import jakarta.validation.constraints.NotBlank;

/**
 * 红测试校验载体：{@code @Valid} 校验失败的请求体 DTO（仅测试源码树）。
 * <p>
 * {@code name} 缺失/空白时触发 {@code MethodArgumentNotValidException}，
 * 由 {@code ExceptionAdviser} 映射为 400 + 字段错误 map（C4）。
 * 校验注解依赖由 {@code com.nona:api} 传递引入的 jakarta.validation-api + hibernate-validator。
 *
 * @author nona9961
 */
public record AdvicePayload(
        @NotBlank(message = "name must not be blank") String name
) {
}
