package com.nona.exceptions;

import com.nona.annotation.ScaffoldGenerated;

/**
 * 自定义业务异常类，用于表示业务逻辑中的异常情况。
 * <p>
 * 可携带业务码（{@link BusinessCode}，小写点分）与可选显式 HTTP 状态码；
 * 未显式指定状态时按业务码默认映射解析，未知业务码兜底 500。
 * ControllerAdvice 中会捕获此类的异常，并返回给前端。
 *
 * @author nona9961
 */
@ScaffoldGenerated
public class BusinessException extends RuntimeException {

    /**
     * 业务码，message-only 构造器创建时为 null。
     */
    private final String businessCode;

    /**
     * HTTP 状态码：显式指定 &gt; 业务码默认映射 &gt; 兜底 500。
     */
    private final int httpStatus;

    /**
     * 仅消息构造器（向后兼容）：业务码为 null，状态兜底 500。
     *
     * @param message 异常消息内容，用于说明业务错误的具体原因和上下文信息
     */
    public BusinessException(String message) {
        this(null, message, BusinessCode.defaultStatus(null));
    }

    /**
     * 业务码 + 消息构造器：状态按业务码默认映射解析。
     *
     * @param businessCode 业务码（小写点分，见 {@link BusinessCode}）
     * @param message      异常消息内容，用于说明业务错误的具体原因和上下文信息
     */
    public BusinessException(String businessCode, String message) {
        this(businessCode, message, BusinessCode.defaultStatus(businessCode));
    }

    /**
     * 业务码 + 消息 + 显式状态构造器：状态以显式指定为准。
     *
     * @param businessCode 业务码（小写点分，见 {@link BusinessCode}）
     * @param message      异常消息内容，用于说明业务错误的具体原因和上下文信息
     * @param httpStatus   显式 HTTP 状态码，优先于业务码默认映射
     */
    public BusinessException(String businessCode, String message, int httpStatus) {
        super(message);
        this.businessCode = businessCode;
        this.httpStatus = httpStatus;
    }

    /**
     * 业务码访问器。
     *
     * @return 业务码；message-only 构造器创建时为 null
     */
    public String getBusinessCode() {
        return businessCode;
    }

    /**
     * HTTP 状态码访问器。
     *
     * @return 解析后的 HTTP 状态码
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
