package com.nona.inf.logging;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.context.ExecutionContext;
import com.nona.inf.context.TraceIdentity;
import org.apache.logging.log4j.core.util.ContextDataProvider;

import java.util.Map;

/**
 * 跟踪身份的日志侧拉取提供者：log4j2 创建日志事件时（调用线程）经
 * {@link ExecutionContext#currentTraceIdentity()} 拉取当前作用域身份，以三键映射注入事件
 * context data，供事件模板按同名键输出 trace 字段。
 * <p>
 * 键名契约（OpenTelemetry 日志字段语义）：{@code trace_id} / {@code span_id} /
 * {@code trace_flags}；无身份时返回空映射——多提供者合并路径对返回值直接 {@code putAll}，
 * 不得返回 {@code null}。
 * <p>
 * 注册：classpath 服务文件
 * {@code META-INF/services/org.apache.logging.log4j.core.util.ContextDataProvider}
 * 声明本类全限定名，由 log4j2 经 {@link java.util.ServiceLoader} 发现。
 *
 * @author nona9961
 */
@ScaffoldGenerated
public class TraceContextDataProvider implements ContextDataProvider {

    /**
     * context data 键：W3C trace-id（事件模板消费的契约键名）。
     */
    static final String TRACE_ID_KEY = "trace_id";

    /**
     * context data 键：W3C span-id（事件模板消费的契约键名）。
     */
    static final String SPAN_ID_KEY = "span_id";

    /**
     * context data 键：W3C trace-flags（事件模板消费的契约键名）。
     */
    static final String TRACE_FLAGS_KEY = "trace_flags";

    /**
     * {@inheritDoc}
     * <p>
     * 拉取时机为事件创建（调用线程）；当前作用域无跟踪身份时返回空映射。
     */
    @Override
    public Map<String, String> supplyContextData() {
        final TraceIdentity identity = ExecutionContext.currentTraceIdentity();
        if (identity == null) {
            return Map.of();
        }
        return Map.of(
                TRACE_ID_KEY, identity.traceId(),
                SPAN_ID_KEY, identity.spanId(),
                TRACE_FLAGS_KEY, identity.traceFlags());
    }
}
