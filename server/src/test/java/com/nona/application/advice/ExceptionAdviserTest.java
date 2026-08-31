package com.nona.application.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 契约测试：{@code ExceptionAdviser} 真实 HTTP 状态码语义（annotation-based）+ MockMvc 契约。
 * <p>
 * 契约要点：
 * <ul>
 *     <li>{@code BusinessException} → 状态 = {@code HttpStatus.valueOf(e.getHttpStatus())}；
 *         响应体 {@code HttpResponse.fail(businessCode, message)}；businessCode 为 null 时
 *         兜底 {@code BusinessCode.INTERNAL_ERROR}</li>
 *     <li>{@code MethodArgumentNotValidException} → 400 + 字段错误 map（{@code BusinessCode.VALIDATION_FAILED}）</li>
 *     <li>{@code NoResourceFoundException}（未匹配路径）→ 404 + {@code BusinessCode.NOT_FOUND}</li>
 *     <li>{@code RuntimeException} 兜底 → 500 + {@code BusinessCode.INTERNAL_ERROR} + 通用消息（不泄露内部细节）</li>
 * </ul>
 * 测试基建结论：
 * <ul>
 *     <li>默认 {@code @AutoConfigureMockMvc(addFilters=true)} 会应用 Boot 默认安全链
 *         （{@code anyRequest().authenticated()} + httpBasic/formLogin），无凭证 MockMvc 请求被
 *         拦截为 401/403，到不了 ExceptionAdviser → 本类固定 {@code addFilters=false}
 *         （测试侧方案，main 零改动）</li>
 *     <li>完整 context（非 standaloneSetup）下 Boot 注册 {@code /**} resource handler，
 *         未匹配路径经 {@code ResourceHttpRequestHandler} 抛 {@code NoResourceFoundException}
 *         （checked ServletException，不落入 RuntimeException 兜底，需显式 handler）→
 *         {@link #unmappedPathShouldMapTo404NotFound()} 成立</li>
 * </ul>
 *
 * @author nona9961
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ExceptionAdviserTest {

    @Autowired
    private MockMvc mockMvc;

    // ---- Happy path：业务码映射状态 ----

    @Test
    @DisplayName("H: BusinessException(NOT_FOUND) → 404 + code=generic.not_found + success=false + message 透传")
    void businessExceptionWithBusinessCodeShouldMapToMappedStatus() throws Exception {
        final MvcResult result = mockMvc.perform(get("/advice/business/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("generic.not_found"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("resource not found"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("H: BusinessException 显式状态 409 优先于业务码默认映射（VALIDATION_FAILED 默认 400）")
    void businessExceptionWithExplicitStatusShouldWinOverBusinessCodeMapping() throws Exception {
        mockMvc.perform(get("/advice/business/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("generic.validation_failed"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("conflict state"));
    }

    @Test
    @DisplayName("F: BusinessException(message-only) → 500 + code=generic.internal_error（businessCode=null 兜底）")
    void messageOnlyBusinessExceptionShouldFallBackToInternalError() throws Exception {
        mockMvc.perform(get("/advice/business/message-only"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("generic.internal_error"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("legacy message only"));
    }

    // ---- Happy path：校验异常 ----

    @Test
    @DisplayName("H: @Valid 校验失败（MethodArgumentNotValidException）→ 400 + code=generic.validation_failed + 字段错误 map")
    void validationFailureShouldReturn400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/advice/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("generic.validation_failed"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.name").value("name must not be blank"));
    }

    // ---- Critical path：未匹配路径 ----

    @Test
    @DisplayName("C: 未匹配路径（NoResourceFoundException）→ 404 + code=generic.not_found + success=false")
    void unmappedPathShouldMapTo404NotFound() throws Exception {
        mockMvc.perform(get("/advice/no-such-mapping"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("generic.not_found"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---- Fail path：运行时兜底 ----

    @Test
    @DisplayName("F: RuntimeException 兜底 → 500 + code=generic.internal_error + 通用消息（不泄露内部细节）")
    void runtimeExceptionShouldReturn500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/advice/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("generic.internal_error"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.message").value(not(containsString("boom-detail-xyz"))));
    }
}
