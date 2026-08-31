package com.nona.exceptions;

import com.nona.annotation.ScaffoldGenerated;

/**
 * 通用业务码及其默认 HTTP 状态映射。
 * <p>
 * 仅承载通用码（{@code generic.*}）；业务域可定义自有码，未登记码的默认状态兜底 500。
 *
 * @author nona9961
 */
@ScaffoldGenerated
public enum BusinessCode {

    /**
     * 通用内部错误。
     */
    INTERNAL_ERROR("generic.internal_error", 500),

    /**
     * 通用参数校验失败。
     */
    VALIDATION_FAILED("generic.validation_failed", 400),

    /**
     * 通用资源未找到。
     */
    NOT_FOUND("generic.not_found", 404);

    /**
     * 未知业务码兜底状态（fail-closed）。
     */
    private static final int DEFAULT_STATUS = 500;

    private final String code;

    private final int defaultStatus;

    BusinessCode(String code, int defaultStatus) {
        this.code = code;
        this.defaultStatus = defaultStatus;
    }

    /**
     * 业务码字符串（小写点分）。
     *
     * @return 业务码值
     */
    public String code() {
        return code;
    }

    /**
     * 查询业务码对应的默认 HTTP 状态码。
     *
     * @param code 业务码字符串，可为 null
     * @return 已知码返回映射状态；未知码或 null 返回 500（fail-closed，不抛异常）
     */
    public static int defaultStatus(String code) {
        for (BusinessCode bc : values()) {
            if (bc.code.equals(code)) {
                return bc.defaultStatus;
            }
        }
        return DEFAULT_STATUS;
    }
}
