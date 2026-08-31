package com.nona.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：{@link HttpResponse} 新形态（code 业务码 String / 成功固定码 success / fail 携带业务码）。
 * <p>
 * 契约要点：
 * <ul>
 *     <li>{@code code} 字段类型 int → String：成功响应固定为 {@code "success"}（形态恒稳定），
 *         失败响应承载业务码</li>
 *     <li>静态工厂：{@code ok()}（无数据/带数据）、{@code fail(businessCode, message)}、
 *         {@code fail(businessCode, message, data)}</li>
 *     <li>record 形态保持；message / success / data 字段保留</li>
 * </ul>
 *
 * @author nona9961
 */
class HttpResponseTest {

    // ---- Happy path ----

    @Test
    @DisplayName("H: ok() 成功响应（无数据）：code=success、success=true、message 保留、data=null")
    void okWithoutDataShouldCarrySuccessCode() {
        final HttpResponse<?> resp = HttpResponse.ok();

        assertThat(resp.code()).isEqualTo(HttpResponse.SUCCESS);
        assertThat(resp.success()).isTrue();
        assertThat(resp.message()).isNotNull();
        assertThat(resp.data()).isNull();
    }

    @Test
    @DisplayName("H: ok(data) 成功响应（带数据）：code=success、data 原样保留")
    void okWithDataShouldCarrySuccessCode() {
        final HttpResponse<?> resp = HttpResponse.ok("payload");

        assertThat(resp.code()).isEqualTo(HttpResponse.SUCCESS);
        assertThat(resp.success()).isTrue();
        assertThat(resp.data()).isEqualTo("payload");
    }

    @Test
    @DisplayName("H: fail(businessCode, message)：code=业务码、success=false、message 原样保留、data=null")
    void failWithBusinessCodeAndMessage() {
        final HttpResponse<?> resp = HttpResponse.fail("generic.validation_failed", "bad input");

        assertThat(resp.code()).isEqualTo("generic.validation_failed");
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).isEqualTo("bad input");
        assertThat(resp.data()).isNull();
    }

    @Test
    @DisplayName("H: fail(businessCode, message, data)：code=业务码、错误数据原样保留")
    void failWithBusinessCodeMessageAndData() {
        final Map<String, String> errors = Map.of("name", "must not be blank");
        final HttpResponse<?> resp = HttpResponse.fail("generic.validation_failed", "bad input", errors);

        assertThat(resp.code()).isEqualTo("generic.validation_failed");
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).isEqualTo("bad input");
        assertThat(resp.data()).isEqualTo(errors);
    }

    // ---- Critical path ----

    @Test
    @DisplayName("C: record 形态保持——code 组件为 String，直接构造后组件访问器可用")
    void recordShapeShouldBeKeptWithStringCode() {
        final HttpResponse<?> resp = new HttpResponse<>("generic.not_found", "not found", false, "detail");

        assertThat(resp.code()).isEqualTo("generic.not_found");
        assertThat(resp.message()).isEqualTo("not found");
        assertThat(resp.success()).isFalse();
        assertThat(resp.data()).isEqualTo("detail");
        assertThat(resp).isInstanceOf(Record.class);
    }
}
