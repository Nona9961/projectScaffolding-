package com.nona.domain.rootPackage.repo;


import com.nona.domain.rootPackage.entity.DomainRoot;
import com.nona.persistence.BaseRepository;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 聚合根仓储接口：定义聚合根的持久化契约，实现在基础设施层。
 */
@ScaffoldGenerated
public interface DomainRootRepository extends BaseRepository<Long, DomainRoot> {
}
