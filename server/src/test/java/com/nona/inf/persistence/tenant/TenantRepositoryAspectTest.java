package com.nona.inf.persistence.tenant;

import com.nona.ProjectApplication;
import com.nona.exceptions.BusinessException;
import com.nona.inf.context.ThreadContext;
import com.nona.inf.persistence.repository.jpa.TestGlobalNoteRepository;
import com.nona.inf.persistence.repository.jpa.TestTenantNoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = ProjectApplication.class)
class TenantRepositoryAspectTest {

    @Autowired
    private TestTenantNoteRepository tenantNoteRepository;

    @Autowired
    private TestGlobalNoteRepository globalNoteRepository;

    @Autowired
    private ThreadContext threadContext;

    @Autowired
    private CrossTenantTestService crossTenantTestService;

    @BeforeEach
    void setUpRequestScope() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        crossTenantTestService.deleteAllNotes();
    }

    @AfterEach
    void tearDown() {
        threadContext.setTenantID(null);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void tenantScopedQueryShouldBeFilteredAndFailClosedWhenTenantMissing() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(1L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);
        assertThat(t1.getTenantID()).isEqualTo("t1");

        threadContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(2L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);
        assertThat(t2.getTenantID()).isEqualTo("t2");

        threadContext.setTenantID("t1");
        List<TestTenantNotePO> visibleToT1 = tenantNoteRepository.findAll();
        assertThat(visibleToT1).hasSize(1);
        assertThat(visibleToT1.get(0).getId()).isEqualTo(1L);

        threadContext.setTenantID(null);
        assertThat(tenantNoteRepository.findAll()).isEmpty();
    }

    @Test
    void tenantScopedFindByIdShouldBeFiltered() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(1L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);

        threadContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(2L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);

        threadContext.setTenantID("t1");
        assertThat(tenantNoteRepository.findById(2L)).isEmpty();

        threadContext.setTenantID("t2");
        assertThat(tenantNoteRepository.findById(2L)).isPresent();

        threadContext.setTenantID(null);
        assertThat(tenantNoteRepository.findById(1L)).isEmpty();
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

        threadContext.setTenantID(null);
        assertThat(globalNoteRepository.findAll()).hasSize(1);
    }

    @Test
    void tenantScopedWriteShouldFailWhenTenantMissing() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID(null);
        TestTenantNotePO po = new TestTenantNotePO();
        po.setId(3L);
        po.setContent("illegal");
        po.setCreateTime(now);
        po.setUpdateTime(now);

        assertThrows(BusinessException.class, () -> tenantNoteRepository.save(po));
    }

    @Test
    void tenantScopedWriteShouldRejectMismatchedTenant() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");
        TestTenantNotePO po = new TestTenantNotePO();
        po.setId(4L);
        po.setTenantID("t2");
        po.setContent("illegal");
        po.setCreateTime(now);
        po.setUpdateTime(now);

        assertThrows(BusinessException.class, () -> tenantNoteRepository.save(po));
    }

    @Test
    void crossTenantShouldBypassTenantIsolationInReadAndBeScopeBound() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(11L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);

        threadContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(12L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);

        threadContext.setTenantID(null);
        assertThat(tenantNoteRepository.findAll()).isEmpty();

        List<TestTenantNotePO> all = crossTenantTestService.listAllNotes();
        assertThat(all).hasSize(2);

        assertThat(tenantNoteRepository.findAll()).isEmpty();
    }

    @Test
    void crossTenantFindByIdShouldBypassIsolation() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(21L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);

        threadContext.setTenantID(null);
        assertThat(tenantNoteRepository.findById(21L)).isEmpty();
        assertThat(crossTenantTestService.getNote(21L)).isNotNull();
    }

    @Test
    void crossTenantWriteShouldBeAllowedWhenExplicitlyEnabled() {
        threadContext.setTenantID("t1");
        crossTenantTestService.saveNoteForTenant("t2", 100L, "note-t2");

        threadContext.setTenantID("t2");
        assertThat(tenantNoteRepository.findAll()).hasSize(1);
    }
}
