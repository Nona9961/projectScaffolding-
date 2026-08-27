package com.nona.tenant;

import com.nona.util.BusinessAssert;

/**
 * 租户写门禁：两条件判定（存储领域模型 I1/I3/I4/I5）。存储无关，零 Spring/JPA 依赖。
 * <p>
 * 纯函数：输入 {@code (实体归属, 当前视角租户, 提权状态)} → 返回「需注入的租户值」或 {@code null}（放行）；
 * 拒绝通过抛出 {@link com.nona.exceptions.BusinessException} 表达（fail-fast）。载体（AOP 切面 / 未来 MyBatis
 * Interceptor）负责取实体、调本类判定、按返回值执行注入。
 * <p>
 * 判定分支（与 017 架构审查决策对齐）：
 * <ul>
 *   <li>哨兵前置：MISSING/ROOT 不可作实体归属（拒绝，防污染，与 minor-1 同源）</li>
 *   <li>提权 + 空归属 → 拒绝（④，I4：插入归属必得，fail-closed——归属不可发明）</li>
 *   <li>提权 + 显式归属 → 放行并保留原值（I1：不写入）</li>
 *   <li>非提权 + context 缺失 → 拒绝（I5：视角缺失 fail-closed）</li>
 *   <li>非提权 + 空归属 → 注入 contextTenant（②，I4：视角补全）</li>
 *   <li>非提权 + 归属不一致 → 拒绝（①，I3：写目标合法性）</li>
 * </ul>
 *
 * @author nona9961
 */
public final class TenantWriteGate {

    /**
     * 视角缺失占位（fail-closed）：租户缺失时 Hibernate resolver 返回该值，不放行 tenant-scoped 数据。
     * <p>
     * 权威定义处：server 侧 {@code TenantContextAccessor.MISSING_TENANT_ID} 为转发常量。
     */
    public static final String MISSING_TENANT_ID = "__MISSING_TENANT__";

    /**
     * 全量视角（root）：读放行/提权作用域下 Hibernate resolver 返回该值以绕过 discriminator 过滤。
     * <p>
     * 权威定义处：server 侧 {@code ThreadContextTenantIdentifierResolver.ROOT_TENANT_ID} 为转发常量。
     */
    public static final String ROOT_TENANT_ID = "__ROOT_TENANT__";

    /**
     * 私有构造：纯函数工具类，禁止实例化。
     */
    private TenantWriteGate() {
    }

    /**
     * 归一化：{@code null} 或空白字符串视为缺失（返回 {@code null}）。
     * <p>
     * 语义迁移自旧 {@code TenantRepositoryAspect#normalizeTenantID}（2026-08-27 架构审查 blocker-1 落点）：
     * 空白在判定前归一为空归属，使「提权空白 → ④ 拒绝」与「非提权空白 → ② 注入」语义收口。
     *
     * @param tenantID 原始 tenantID
     * @return 归一化后的 tenantID；缺失则返回 {@code null}
     */
    private static String normalizeTenantID(String tenantID) {
        if (tenantID == null || tenantID.isBlank()) {
            return null;
        }
        return tenantID;
    }

    /**
     * 两条件判定（设计 D1/D2）：依「提权状态 × 实体归属」判定本次写目标。
     *
     * @param ownedTenantId 实体归属（tenant-scoped PO 的 tenantID）；可为 {@code null}
     * @param contextTenant 当前视角租户（上下文）；可为 {@code null}
     * @param elevated      是否处于提权作用域（写门禁唯一判断源）
     * @return 需注入实体的租户值（② 视角补全时）；否则返回 {@code null}（放行，不写入）
     * @throws com.nona.exceptions.BusinessException 拒绝时抛出（④/哨兵/I5/①）
     */
    public static String decideInjection(String ownedTenantId,
                                         String contextTenant,
                                         boolean elevated) {
        final String owned = normalizeTenantID(ownedTenantId); // 空白归一为空归属（④ 拒绝 / ② 注入语义收口）
        assertNotSentinel(owned);                              // 哨兵拒绝前置：提权/非提权统一语义（审查 minor-1）
        if (elevated) {
            if (owned == null) {
                throw BusinessAssert.generateExByMsg(
                        "elevated write requires explicit tenantId but tenant is missing or blank"); // ④ I4
            }
            return null;                                       // 显式归属放行（I1：不写入）
        }
        if (contextTenant == null) {
            throw BusinessAssert.generateExByMsg("tenantID is required for tenant-scoped write");   // I5
        }
        if (owned == null) {
            return contextTenant;                              // ② 视角补全（I4，含空白归一）
        }
        if (!contextTenant.equals(owned)) {
            throw BusinessAssert.generateExByMsg(
                    "cross-tenant write is forbidden. currentTenant={}, entityTenant={}",
                    contextTenant, owned);                     // ① I3
        }
        return null;
    }

    /**
     * 判断是否为哨兵值（MISSING/ROOT）。
     *
     * @param v 待判断值；{@code null} 返回 false
     * @return 是哨兵值返回 true
     */
    private static boolean isSentinel(String v) {
        return MISSING_TENANT_ID.equals(v) || ROOT_TENANT_ID.equals(v);
    }

    /**
     * 哨兵断言：实体归属不可为哨兵值（防污染）。{@code null} 天然跳过（另由 ④/② 分支处理）。
     *
     * @param v 归一化后的实体归属；可为 {@code null}
     * @throws com.nona.exceptions.BusinessException 为哨兵值时抛出
     */
    private static void assertNotSentinel(String v) {
        if (v != null && isSentinel(v)) {
            throw BusinessAssert.generateExByMsg("invalid tenantId cannot be used as entity tenant: {}", v);
        }
    }
}
