package com.nona.inf.persistence.repository.jpa;

import com.nona.inf.persistence.tenant.TestTenantNotePO;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestTenantNoteRepository extends JpaRepository<TestTenantNotePO, Long> {
}

