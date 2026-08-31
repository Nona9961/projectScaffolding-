package com.nona.application.advice;

import com.nona.exceptions.BusinessCode;
import com.nona.exceptions.BusinessException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 红测试专用异常触发源（测试基建，仅存在于测试源码树，main 零改动）。
 * <p>
 * 脚手架 main 无任何 Controller，MockMvc 契约测试需由本类显式触发各类异常：
 * <ul>
 *     <li>{@code GET /advice/business/not-found}：BusinessException + {@link BusinessCode#NOT_FOUND}
 *         （未显式状态 → 业务码默认映射 404，C2/C3）</li>
 *     <li>{@code GET /advice/business/conflict}：BusinessException + 显式 409
 *         （显式状态优先于业务码默认映射，C2）</li>
 *     <li>{@code GET /advice/business/message-only}：message-only 构造器（businessCode=null → 兜底 500，C2）</li>
 *     <li>{@code POST /advice/validate}：{@code @Valid} 触发 {@code MethodArgumentNotValidException}</li>
 *     <li>{@code GET /advice/runtime}：未处理运行时异常（兜底 500，不泄露内部细节）</li>
 * </ul>
 * 本类位于 {@code com.nona} 包下，被 {@code @SpringBootTest} 组件扫描注册为普通 Controller；
 * 路径前缀 {@code /advice/**} 与 main 代码零重叠。消息使用 ASCII 英文：
 * 测试经 {@code @AutoConfigureMockMvc(addFilters=false)} 无 CharacterEncodingFilter，
 * 避免响应编码不确定性污染断言。
 *
 * @author nona9961
 */
@RestController
public class AdviceTriggerController {

    @GetMapping("/advice/business/not-found")
    public String businessNotFound() {
        throw new BusinessException(BusinessCode.NOT_FOUND.code(), "resource not found");
    }

    @GetMapping("/advice/business/conflict")
    public String businessConflict() {
        throw new BusinessException(BusinessCode.VALIDATION_FAILED.code(), "conflict state", 409);
    }

    @GetMapping("/advice/business/message-only")
    public String businessMessageOnly() {
        throw new BusinessException("legacy message only");
    }

    @PostMapping("/advice/validate")
    public String validate(@Valid @RequestBody AdvicePayload payload) {
        return "ok";
    }

    @GetMapping("/advice/runtime")
    public String runtime() {
        throw new IllegalStateException("boom-detail-xyz");
    }
}
