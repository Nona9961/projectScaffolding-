package com.nona.inf.persistence.tenant;

import com.nona.inf.context.CrossTenant;
import com.nona.inf.persistence.repository.jpa.TestTenantNoteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
class CrossTenantTestService {

    private final TestTenantNoteRepository tenantNoteRepository;

    CrossTenantTestService(TestTenantNoteRepository tenantNoteRepository) {
        this.tenantNoteRepository = tenantNoteRepository;
    }

    @CrossTenant
    List<TestTenantNotePO> listAllNotes() {
        return tenantNoteRepository.findAll();
    }

    @CrossTenant
    TestTenantNotePO getNote(long id) {
        return tenantNoteRepository.findById(id).orElse(null);
    }

    @CrossTenant
    TestTenantNotePO saveNoteForTenant(String tenantID, long id, String content) {
        LocalDateTime now = LocalDateTime.now();
        TestTenantNotePO po = new TestTenantNotePO();
        po.setId(id);
        po.setTenantID(tenantID);
        po.setContent(content);
        po.setCreateTime(now);
        po.setUpdateTime(now);
        return tenantNoteRepository.save(po);
    }
}
