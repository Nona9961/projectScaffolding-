package com.nona.inf.persistence.repository.jpa;

import com.nona.inf.persistence.tenant.TestGlobalNotePO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 租户隔离测试用的 global repository。
 *
 * @author nona
 */
public interface TestGlobalNoteRepository extends JpaRepository<TestGlobalNotePO, Long> {
}