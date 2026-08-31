package com.nona.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：{@link BusinessException} 新形态（业务码 + 可选显式 HTTP 状态码，零 spring 依赖）。
 * <p>
 * 契约要点：
 * <ul>
 *     <li>保留 message-only 构造器（向后兼容：JacksonUtil/BusinessAssert/既有测试）</li>
 *     <li>新增 {@code BusinessException(String businessCode, String message)}、
 *         {@code BusinessException(String businessCode, String message, int httpStatus)}</li>
 *     <li>状态解析顺序：显式 httpStatus &gt; BusinessCode 默认映射 &gt; 未知码默认 500</li>
 *     <li>访问器：{@code getBusinessCode()} / {@code getHttpStatus()}</li>
 * </ul>
 *
 * @author nona9961
 */
class BusinessExceptionTest {

    // ---- Happy path ----

    @Test
    @DisplayName("H: (businessCode, message) 构造：业务码保留、状态按 BusinessCode 默认映射（not_found → 404）")
    void businessCodeShouldMapToDefaultStatus() {
        BusinessException ex = new BusinessException("generic.not_found", "order not found");

        assertThat(ex.getBusinessCode()).isEqualTo("generic.not_found");
        assertThat(ex.getHttpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("H: message-only 构造器向后兼容：消息保留、getHttpStatus 默认 500、仍是 RuntimeException")
    void messageOnlyConstructorShouldRemainCompatible() {
        BusinessException ex = new BusinessException("legacy message");

        assertThat(ex.getMessage()).isEqualTo("legacy message");
        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    // ---- Critical path ----

    @Test
    @DisplayName("C: 显式 httpStatus 优先于 BusinessCode 默认映射（validation_failed 映射 400，显式 422 生效）")
    void explicitHttpStatusShouldWinOverMappedStatus() {
        BusinessException ex = new BusinessException("generic.validation_failed", "bad input", 422);

        assertThat(ex.getHttpStatus()).isEqualTo(422);
        assertThat(ex.getBusinessCode()).isEqualTo("generic.validation_failed");
    }

    @Test
    @DisplayName("C: generic.internal_error 经映射解析为 500")
    void internalErrorShouldMapTo500() {
        BusinessException ex = new BusinessException("generic.internal_error", "internal boom");

        assertThat(ex.getHttpStatus()).isEqualTo(500);
    }

    // ---- Fail path ----

    @Test
    @DisplayName("F: 未知业务码无显式状态时兜底 500")
    void unknownBusinessCodeShouldFallBackTo500() {
        BusinessException ex = new BusinessException("unknown.reason", "boom");

        assertThat(ex.getHttpStatus()).isEqualTo(500);
        assertThat(ex.getBusinessCode()).isEqualTo("unknown.reason");
    }
}
