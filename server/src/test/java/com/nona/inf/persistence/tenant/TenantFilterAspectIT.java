package com.nona.inf.persistence.tenant;

import com.nona.ProjectApplication;
import com.nona.exceptions.BusinessException;
import com.nona.inf.context.TenantContext;
import com.nona.inf.persistence.repository.jpa.TestGlobalNoteRepository;
import com.nona.inf.persistence.repository.jpa.TestTenantNoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = ProjectApplication.class)
@Transactional
class TenantFilterAspectIT {

    @Autowired
    private TestTenantNoteRepository tenantNoteRepository;

    @Autowired
    private TestGlobalNoteRepository globalNoteRepository;

    @Autowired
    private CrossTenantTestService crossTenantTestService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tenantScopedQueryShouldBeFilteredAndFailClosedWhenTenantMissing() {
        LocalDateTime now = LocalDateTime.now();

        TenantContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(1L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);
        assertThat(t1.getTenantID()).isEqualTo("t1");

        TenantContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(2L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);
        assertThat(t2.getTenantID()).isEqualTo("t2");

        TenantContext.setTenantID("t1");
        List<TestTenantNotePO> visibleToT1 = tenantNoteRepository.findAll();
        assertThat(visibleToT1).hasSize(1);
        assertThat(visibleToT1.get(0).getId()).isEqualTo(1L);

        TenantContext.clear();
        assertThat(tenantNoteRepository.findAll()).isEmpty();
    }

    @Test
    void crossTenantShouldBypassAutomaticFilter() {
        LocalDateTime now = LocalDateTime.now();

        TenantContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(1L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);

        TenantContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(2L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);

        TenantContext.clear();
        assertThat(tenantNoteRepository.findAll()).isEmpty();

        List<TestTenantNotePO> all = crossTenantTestService.listAllNotes();
        assertThat(all).hasSize(2);

        // 作用域应被正确收敛：@CrossTenant 仅在标注的方法内生效
        assertThat(TenantContext.isCrossTenant()).isFalse();
        assertThat(tenantNoteRepository.findAll()).isEmpty();
    }

    @Test
    void crossTenantWriteShouldBeAllowedWhenExplicitlyEnabled() {
        TenantContext.setTenantID("t1");
        crossTenantTestService.saveNoteForTenant("t2", 100L, "note-t2");

        TenantContext.setTenantID("t2");
        assertThat(tenantNoteRepository.findAll()).hasSize(1);
    }

    @Test
    void tenantScopedFindByIdShouldBeFiltered() {
        LocalDateTime now = LocalDateTime.now();

        TenantContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(1L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);

        TenantContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(2L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);

        TenantContext.setTenantID("t1");
        assertThat(tenantNoteRepository.findById(2L)).isEmpty();

        TenantContext.setTenantID("t2");
        assertThat(tenantNoteRepository.findById(2L)).isPresent();

        TenantContext.clear();
        assertThat(tenantNoteRepository.findById(1L)).isEmpty();
        assertThat(crossTenantTestService.getNote(1L)).isNotNull();
    }

    @Test
    void globalQueryShouldNotBeFilteredWhenTenantMissing() {
        LocalDateTime now = LocalDateTime.now();

        TestGlobalNotePO global = new TestGlobalNotePO();
        global.setId(10L);
        global.setContent("global");
        global.setCreateTime(now);
        global.setUpdateTime(now);
        globalNoteRepository.save(global);

        TenantContext.clear();
        assertThat(globalNoteRepository.findAll()).hasSize(1);
    }

    @Test
    void crossTenantWriteShouldBeRejected() {
        LocalDateTime now = LocalDateTime.now();

        TenantContext.setTenantID("t1");
        TestTenantNotePO po = new TestTenantNotePO();
        po.setId(3L);
        po.setTenantID("t2");
        po.setContent("illegal");
        po.setCreateTime(now);
        po.setUpdateTime(now);

        assertThrows(BusinessException.class, () -> tenantNoteRepository.save(po));
    }
}
