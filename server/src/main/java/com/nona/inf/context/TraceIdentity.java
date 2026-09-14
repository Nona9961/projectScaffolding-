package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;

import java.util.Objects;

/**
 * 跟踪身份的不可变值对象（W3C trace-id / span-id / trace-flags 三分量）。
 * <p>
 * 三分量整体语义：不存在「半个跟踪身份」——构造即拒绝 {@code null} 分量，
 * 「不存在」由整个值为 {@code null} 表达。供执行上下文与跨线程快照共享。
 *
 * @param traceId    W3C trace-id；不得为 {@code null}
 * @param spanId     W3C span-id；不得为 {@code null}
 * @param traceFlags W3C trace-flags；不得为 {@code null}
 */
@ScaffoldGenerated
public record TraceIdentity(String traceId, String spanId, String traceFlags) {

    /**
     * 紧凑构造器：三分量非空——部分三元组构成不一致窗口，构造即拒绝。
     *
     * @throws NullPointerException 任一分量为 {@code null} 时抛出
     */
    public TraceIdentity {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(spanId, "spanId must not be null");
        Objects.requireNonNull(traceFlags, "traceFlags must not be null");
    }
}
