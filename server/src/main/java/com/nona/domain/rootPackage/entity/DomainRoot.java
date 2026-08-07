package com.nona.domain.rootPackage.entity;

import lombok.RequiredArgsConstructor;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 聚合根示例：承载业务不变量与状态流转。
 * <p>
 * 所有聚合根创建必须经由 Factory（配合 {@code IDUtils.generateID()}）。
 *
 * @author nona9961
 * @since 2025/3/19
 */
@RequiredArgsConstructor
@ScaffoldGenerated
public class DomainRoot {

    /**
     * 聚合根 ID
     */
    private final Long id;

    /**
     * 业务状态
     */
    private SomeStatus someStatus;

    /**
     * 配置业务状态。
     *
     * @param someStatus 新状态
     */
    public void configureSomeStatus(SomeStatus someStatus) {
        this.someStatus = someStatus;
    }


}
