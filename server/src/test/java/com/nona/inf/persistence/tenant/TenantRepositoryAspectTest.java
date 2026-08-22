package com.nona.inf.persistence.tenant;

import com.nona.ProjectApplication;
import com.nona.exceptions.BusinessException;
import com.nona.inf.context.TenantContextAccessor;
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
import com.nona.annotation.ScaffoldGenerated;

@SpringBootTest(classes = ProjectApplication.class)
@ScaffoldGenerated
class TenantRepositoryAspectTest {

    @Autowired
    private TestTenantNoteRepository tenantNoteRepository;

    @Autowired
    private TestGlobalNoteRepository globalNoteRepository;

    @Autowired
    private ThreadContext threadContext;

    @Autowired
    private CrossTenantTestService crossTenantTestService;

    /**
     * 初始化 request scope（模拟 Web 请求）并清理测试数据。
     */
    @BeforeEach
    void setUpRequestScope() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        crossTenantTestService.deleteAllNotes();
    }

    /**
     * 清理测试现场，避免用例互相污染。
     */
    @AfterEach
    void tearDown() {
        threadContext.setTenantID(null);
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 验证 tenant-scoped 的 findAll 在不同 tenant 下隔离；tenant 缺失/空白时 fail-closed 返回空。
     */
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

        threadContext.setTenantID("   ");
        assertThat(tenantNoteRepository.findAll()).isEmpty();
    }

    /**
     * 验证 tenant-scoped 的 findById 在不同 tenant 下隔离；tenant 缺失/空白时 fail-closed。
     */
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

        threadContext.setTenantID("   ");
        assertThat(tenantNoteRepository.findById(1L)).isEmpty();
    }

    /**
     * 验证 tenant-scoped 的 count/existsById 在不同 tenant 下隔离；提权作用域下可跨租户统计。
     */
    @Test
    void tenantScopedCountAndExistsShouldBeFiltered() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(31L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);

        threadContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(32L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);

        threadContext.setTenantID("t1");
        assertThat(tenantNoteRepository.count()).isEqualTo(1);
        assertThat(tenantNoteRepository.existsById(31L)).isTrue();
        assertThat(tenantNoteRepository.existsById(32L)).isFalse();

        threadContext.setTenantID("t2");
        assertThat(tenantNoteRepository.count()).isEqualTo(1);
        assertThat(tenantNoteRepository.existsById(32L)).isTrue();

        threadContext.setTenantID(null);
        assertThat(tenantNoteRepository.count()).isZero();
        assertThat(tenantNoteRepository.existsById(31L)).isFalse();

        assertThat(crossTenantTestService.countAllNotes()).isEqualTo(2);
        assertThat(crossTenantTestService.noteExists(31L)).isTrue();
        assertThat(crossTenantTestService.noteExists(32L)).isTrue();

        assertThat(tenantNoteRepository.count()).isZero();
    }

    /**
     * 验证 global entity 不受 tenant filter 影响（tenant 缺失/空白仍可查询）。
     */
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

        threadContext.setTenantID("   ");
        assertThat(globalNoteRepository.findAll()).hasSize(1);
    }

    /**
     * 验证 tenant 缺失时 tenant-scoped 写入拒绝。
     */
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

    /**
     * 验证 tenant 空白时 tenant-scoped 写入拒绝。
     */
    @Test
    void tenantScopedWriteShouldFailWhenTenantBlank() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID(" ");
        TestTenantNotePO po = new TestTenantNotePO();
        po.setId(41L);
        po.setContent("illegal");
        po.setCreateTime(now);
        po.setUpdateTime(now);

        assertThrows(BusinessException.class, () -> tenantNoteRepository.save(po));
    }

    /**
     * 验证当前 tenant 与 entity tenant 不一致时写入拒绝。
     */
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

    /**
     * 验证 saveAll 会为 tenant-scoped 实体注入 tenantID（实体 tenant 为空/空白）。
     */
    @Test
    void tenantScopedSaveAllShouldInjectTenantID() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");

        TestTenantNotePO po1 = new TestTenantNotePO();
        po1.setId(51L);
        po1.setContent("note-1");
        po1.setCreateTime(now);
        po1.setUpdateTime(now);

        TestTenantNotePO po2 = new TestTenantNotePO();
        po2.setId(52L);
        po2.setTenantID("   ");
        po2.setContent("note-2");
        po2.setCreateTime(now);
        po2.setUpdateTime(now);

        tenantNoteRepository.saveAll(List.of(po1, po2));

        assertThat(po1.getTenantID()).isEqualTo("t1");
        assertThat(po2.getTenantID()).isEqualTo("t1");
        assertThat(tenantNoteRepository.count()).isEqualTo(2);
    }

    /**
     * 验证 saveAll 中存在 tenant 不一致实体时整体拒绝。
     */
    @Test
    void tenantScopedSaveAllShouldRejectMismatchedTenant() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");

        TestTenantNotePO po1 = new TestTenantNotePO();
        po1.setId(61L);
        po1.setContent("note-1");
        po1.setCreateTime(now);
        po1.setUpdateTime(now);

        TestTenantNotePO po2 = new TestTenantNotePO();
        po2.setId(62L);
        po2.setTenantID("t2");
        po2.setContent("note-2");
        po2.setCreateTime(now);
        po2.setUpdateTime(now);

        assertThrows(BusinessException.class, () -> tenantNoteRepository.saveAll(List.of(po1, po2)));
        assertThat(crossTenantTestService.listAllNotes()).isEmpty();
    }

    /**
     * 验证提权作用域可绕过 tenant 读隔离，且作用域必须收敛（离开作用域后恢复）。
     */
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

    /**
     * 验证提权作用域内 listAllNotes 全量可见（跨 t1/t2 数据均能查到），出作用域后立即恢复隔离。
     */
    @Test
    void elevatedScopeShouldExposeAllTenantsInsideAndRestoreIsolationAfterExit() {
        LocalDateTime now = LocalDateTime.now();

        threadContext.setTenantID("t1");
        TestTenantNotePO t1 = new TestTenantNotePO();
        t1.setId(91L);
        t1.setContent("note-t1");
        t1.setCreateTime(now);
        t1.setUpdateTime(now);
        tenantNoteRepository.save(t1);

        threadContext.setTenantID("t2");
        TestTenantNotePO t2 = new TestTenantNotePO();
        t2.setId(92L);
        t2.setContent("note-t2");
        t2.setCreateTime(now);
        t2.setUpdateTime(now);
        tenantNoteRepository.save(t2);

        assertThat(tenantNoteRepository.findAll()).hasSize(1);

        List<TestTenantNotePO> all = crossTenantTestService.listAllNotes();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(TestTenantNotePO::getId).containsExactlyInAnyOrder(91L, 92L);

        assertThat(tenantNoteRepository.findAll()).hasSize(1);
        assertThat(tenantNoteRepository.findAll().get(0).getId()).isEqualTo(92L);
    }

    /**
     * 验证提权作用域下 findById 可跨租户读取。
     */
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

    /**
     * 验证提权作用域允许跨租户写（显式提供 entity tenantID，保留原值）。
     */
    @Test
    void crossTenantWriteShouldKeepExplicitTenantID() {
        threadContext.setTenantID("t1");
        crossTenantTestService.saveNoteForTenant("t2", 100L, "note-t2");

        threadContext.setTenantID("t2");
        assertThat(tenantNoteRepository.findAll()).hasSize(1);
    }

    /**
     * 验证提权作用域 + entity tenantID 缺失 → 注入当前 tenant（admin 普通创建场景）。
     */
    @Test
    void crossTenantWriteShouldInjectCurrentTenantWhenEntityTenantMissing() {
        threadContext.setTenantID("t1");
        crossTenantTestService.saveNoteWithoutTenantID(71L, "note-from-admin");

        threadContext.setTenantID("t1");
        assertThat(tenantNoteRepository.findAll()).hasSize(1);
        assertThat(tenantNoteRepository.findAll().get(0).getContent()).isEqualTo("note-from-admin");
    }

    /**
     * 验证提权作用域 + 当前 tenant 缺失 + 实体携带 MISSING 占位 → 写入拒绝（fail-closed）。
     */
    @Test
    void crossTenantWriteShouldRejectMissingPlaceholderEntityTenant() {
        threadContext.setTenantID(null);
        assertThrows(BusinessException.class,
                () -> crossTenantTestService.saveNoteForTenant(TenantContextAccessor.MISSING_TENANT_ID, 81L, "illegal"));
        assertThat(crossTenantTestService.listAllNotes()).isEmpty();
    }

    /**
     * 验证提权作用域 + 当前 tenant 缺失 + 实体显式合法 tenant → 放行且保留原值（C 端跨店下单场景）。
     */
    @Test
    void crossTenantWriteShouldKeepExplicitTenantWhenContextTenantMissing() {
        threadContext.setTenantID(null);
        crossTenantTestService.saveNoteForTenant("t1", 100L, "explicit");

        List<TestTenantNotePO> all = crossTenantTestService.listAllNotes();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getTenantID()).isEqualTo("t1");
        assertThat(all.get(0).getContent()).isEqualTo("explicit");
    }

    /**
     * 验证提权作用域 + 当前 tenant 缺失 + 实体未携带 tenant → 拒绝（双缺失 fail-closed 回归保护）。
     */
    @Test
    void crossTenantWriteShouldRejectWhenBothMissing() {
        threadContext.setTenantID(null);

        assertThrows(BusinessException.class, () -> crossTenantTestService.saveNoteWithoutTenantID(82L, "illegal"));
        assertThat(crossTenantTestService.listAllNotes()).isEmpty();
    }
}