package com.nona.domain.rootPackage.factory;

import com.nona.domain.rootPackage.entity.DomainRoot;
import com.nona.domain.rootPackage.entity.SomeStatus;
import org.apache.commons.lang3.RandomUtils;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 聚合根工厂示例：负责聚合根创建与状态初始化。
 *
 * @author nona9961
 * @since 2025/3/19
 */
@ScaffoldGenerated
public class DomainRootFactory {

    /**
     * 创建聚合根并初始化业务状态。
     *
     * @param someStatus 初始业务状态
     * @return 创建好的聚合根
     */
    public DomainRoot create(SomeStatus someStatus) {
        final long id = RandomUtils.insecure().randomLong();
        final DomainRoot domainRoot = new DomainRoot(id);
        domainRoot.configureSomeStatus(someStatus);
        return domainRoot;
    }

}
