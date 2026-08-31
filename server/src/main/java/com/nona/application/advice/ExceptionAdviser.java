package com.nona.application.advice;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.api.HttpResponse;
import com.nona.exceptions.BusinessCode;
import com.nona.exceptions.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器：统一捕获异常并转换为真实 HTTP 状态码 + {@link HttpResponse} 响应体。
 * <p>
 * HTTP 语义（状态映射表）：
 * <ul>
 *     <li>{@link BusinessException} → 状态 = {@code e.getHttpStatus()}（显式状态 &gt; 业务码默认映射 &gt; 兜底 500）；
 *         响应体携带业务码；businessCode 为 null（message-only）时兜底 {@link BusinessCode#INTERNAL_ERROR}</li>
 *     <li>{@link MethodArgumentNotValidException} / {@link BindException} → 400 + 字段错误 map
 *         （{@link BusinessCode#VALIDATION_FAILED}）</li>
 *     <li>{@link NoResourceFoundException}（未匹配路径）→ 404（{@link BusinessCode#NOT_FOUND}）</li>
 *     <li>{@link RuntimeException} 兜底 → 500（{@link BusinessCode#INTERNAL_ERROR}），
 *         通用消息不泄露内部细节，堆栈仅记录在服务端日志</li>
 * </ul>
 * HTTP 状态码由本类集中设置：业务层与响应体不承载状态码；
 * 业务 Controller 一律返回 {@link HttpResponse}，禁止直接使用 {@code ResponseEntity}。
 */
@RestControllerAdvice
@Slf4j
@ScaffoldGenerated
public class ExceptionAdviser {

    /**
     * 处理业务异常：状态按显式指定 &gt; 业务码默认映射解析，消息与业务码透传。
     * <p>
     * 状态为动态值，使用 {@link ResponseEntity#status} 设置（静态 {@code @ResponseStatus} 无法表达）。
     *
     * @param e 业务异常
     * @return 失败响应（携带业务码与异常消息），状态由 {@code e.getHttpStatus()} 决定
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<HttpResponse<?>> handleBusinessException(BusinessException e) {
        final String businessCode = e.getBusinessCode() != null ? e.getBusinessCode() : BusinessCode.INTERNAL_ERROR.code();
        return ResponseEntity.status(HttpStatus.valueOf(e.getHttpStatus()))
                .body(HttpResponse.fail(businessCode, e.getMessage()));
    }

    /**
     * 处理方法参数校验异常：返回 400 + 字段级错误 map。
     *
     * @param ex 参数校验异常
     * @return 失败响应（业务码 {@link BusinessCode#VALIDATION_FAILED} + 字段错误 map）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HttpResponse<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        final Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.error("illegal args :{}", errors);
        return HttpResponse.fail(BusinessCode.VALIDATION_FAILED.code(), "illegal args", errors);
    }

    /**
     * 处理表单绑定异常：返回 400 + 字段级错误 map。
     *
     * @param ex 绑定异常
     * @return 失败响应（业务码 {@link BusinessCode#VALIDATION_FAILED} + 字段错误 map）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public HttpResponse<?> handleBindException(BindException ex) {
        final Map<String, String> errors = new HashMap<>();
        ex.getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.error("illegal args :{}", errors);
        return HttpResponse.fail(BusinessCode.VALIDATION_FAILED.code(), "illegal args", errors);
    }

    /**
     * 处理未匹配路径异常（Spring MVC 静态资源解析抛出）：返回 404。
     *
     * @param e 未匹配路径异常
     * @return 失败响应（业务码 {@link BusinessCode#NOT_FOUND}）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public HttpResponse<?> handleNoResourceFound(NoResourceFoundException e) {
        return HttpResponse.fail(BusinessCode.NOT_FOUND.code(), "Resource not found");
    }

    /**
     * 兜底处理运行时异常：记录堆栈，返回通用错误消息（不泄露内部细节）。
     *
     * @param e 运行时异常
     * @return 失败响应（业务码 {@link BusinessCode#INTERNAL_ERROR} + 通用消息）
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public HttpResponse<?> handleRuntimeException(RuntimeException e) {
        log.error("unhandled exception : {}", e.getMessage());
        log.error("stack is ", e);
        return HttpResponse.fail(BusinessCode.INTERNAL_ERROR.code(), "Severe internal error. Please retry later.");
    }
}
