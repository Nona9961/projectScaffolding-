package com.nona.inf.persistence.tracking;

import com.nona.changeTracking.domain.model.unitofwork.UnitOfWork;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * UnitOfWork自动配置
 * <p>
 * 当用户未定义UnitOfWorkProvider时，自动从change-tracking配置创建
 */
@AutoConfiguration
@ConditionalOnClass({UnitOfWork.class, UnitOfWorkProvider.class})
@ConditionalOnMissingBean(UnitOfWorkProvider.class)
public class UnitOfWorkAutoConfiguration {

    /**
     * 创建UnitOfWorkProvider
     *
     * @param props 变更追踪配置属性
     * @return UnitOfWorkProvider实例
     * @throws IllegalStateException 当配置类无法加载时抛出
     */
    public static UnitOfWorkProvider createProvider(
            final ChangeTrackingProperties props) {
        return UnitOfWorkProvider.builder()
                .fromProperties(props)
                .build();
    }
}
