package com.nona.inf.persistence.tenant;

import com.nona.inf.context.CrossTenant;
import com.nona.inf.persistence.repository.jpa.TestGlobalNoteRepository;
import com.nona.inf.persistence.repository.jpa.TestTenantNoteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
class CrossTenantTestService {

    private final TestTenantNoteRepository tenantNoteRepository;
    private final TestGlobalNoteRepository globalNoteRepository;

    CrossTenantTestService(TestTenantNoteRepository tenantNoteRepository, TestGlobalNoteRepository globalNoteRepository) {
        this.tenantNoteRepository = tenantNoteRepository;
        this.globalNoteRepository = globalNoteRepository;
    }

    @CrossTenant
    void deleteAllNotes() {
        tenantNoteRepository.deleteAll();
        globalNoteRepository.deleteAll();
    }

    @CrossTenant
    List<TestTenantNotePO> listAllNotes() {
        return tenantNoteRepository.findAll();
    }

    @CrossTenant
    TestTenantNotePO getNote(Long id) {
        return tenantNoteRepository.findById(id).orElse(null);
    }

    @CrossTenant
    void saveNoteForTenant(String tenantID, Long id, String content) {
        LocalDateTime now = LocalDateTime.now();

        TestTenantNotePO po = new TestTenantNotePO();
        po.setId(id);
        po.setTenantID(tenantID);
        po.setContent(content);
        po.setCreateTime(now);
        po.setUpdateTime(now);
        tenantNoteRepository.save(po);
    }
}
