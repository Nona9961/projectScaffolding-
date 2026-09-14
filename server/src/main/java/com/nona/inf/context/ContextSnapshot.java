package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.changeTracking.domain.model.tracking.BaselineSnapshot;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * 跨线程执行上下文快照（不可变 record）：一次执行中安全且异步必需的上下文字段集。
 * <p>
 * 承载三元组（tenantID / role / identity）、追踪基线与跟踪身份；attributes 与根对象
 * 快照被有意排除——它们可能携带作用域可变状态。
 *
 * @param tenantID         租户 ID；可能为 null
 * @param role             角色列表；可能为 null
 * @param identity         请求者身份；可能为 null
 * @param trackingBaseline 追踪基线（深拷贝）；无追踪器时为 null
 * @param traceIdentity    跟踪身份；无跟踪身份时为 null（三分量整体缺失）
 */
@ScaffoldGenerated
public record ContextSnapshot(
        @Nullable String tenantID,
        @Nullable List<String> role,
        @Nullable String identity,
        @Nullable BaselineSnapshot trackingBaseline,
        @Nullable TraceIdentity traceIdentity
) {

    /** 表示缺失 / 已清除上下文的哨兵快照（三元组、追踪基线与跟踪身份均为空）。 */
    public static final ContextSnapshot EMPTY = new ContextSnapshot(null, null, null, null);

    /**
     * 兼容便捷构造器：三元组 + 追踪基线（跟踪身份缺省为 {@code null}）。
     *
     * @param tenantID         租户 ID；可能为 null
     * @param role             角色列表；可能为 null
     * @param identity         请求者身份；可能为 null
     * @param trackingBaseline 追踪基线（深拷贝）；无追踪器时为 null
     */
    public ContextSnapshot(
            @Nullable String tenantID,
            @Nullable List<String> role,
            @Nullable String identity,
            @Nullable BaselineSnapshot trackingBaseline) {
        this(tenantID, role, identity, trackingBaseline, null);
    }

    /**
     * 兼容便捷构造器：仅三元组（追踪基线与跟踪身份缺省为 {@code null}）。
     *
     * @param tenantID 租户 ID；可能为 null
     * @param role     角色列表；可能为 null
     * @param identity 请求者身份；可能为 null
     */
    public ContextSnapshot(
            @Nullable String tenantID,
            @Nullable List<String> role,
            @Nullable String identity) {
        this(tenantID, role, identity, null);
    }
}
