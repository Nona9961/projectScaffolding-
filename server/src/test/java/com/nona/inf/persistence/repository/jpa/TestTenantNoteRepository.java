package com.nona.inf.persistence.repository.jpa;

import com.nona.inf.persistence.tenant.TestTenantNotePO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 租户隔离测试用的 tenant-scoped repository。
 *
 * @author nona
 */
public interface TestTenantNoteRepository extends JpaRepository<TestTenantNotePO, Long> {
}