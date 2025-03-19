package com.nona.domain.rootPackage.factory;

import com.nona.domain.rootPackage.entity.DomainRoot;
import com.nona.domain.rootPackage.entity.SomeStatus;
import org.apache.commons.lang3.RandomUtils;

/**
 * @author nona9961
 * @since 2025/3/19
 */
public class DomainRootFactory {

    public DomainRoot create(SomeStatus someStatus) {
        final long id = RandomUtils.insecure().randomLong();
        final DomainRoot domainRoot = new DomainRoot(id);
        domainRoot.configureSomeStatus(someStatus);
        return domainRoot;
    }

}
