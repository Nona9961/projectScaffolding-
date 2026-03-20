package com.nona.inf.persistence.repository.jpa;

import com.nona.inf.persistence.tenant.TestGlobalNotePO;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestGlobalNoteRepository extends JpaRepository<TestGlobalNotePO, Long> {
}

