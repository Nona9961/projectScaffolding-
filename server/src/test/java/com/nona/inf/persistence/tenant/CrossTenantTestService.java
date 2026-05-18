package com.nona.inf.persistence.tenant;

import com.nona.inf.context.CrossTenant;
import com.nona.inf.persistence.repository.jpa.TestGlobalNoteRepository;
import com.nona.inf.persistence.repository.jpa.TestTenantNoteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import com.nona.annotation.ScaffoldGenerated;

@Service
@ScaffoldGenerated
class CrossTenantTestService {

    private final TestTenantNoteRepository tenantNoteRepository;
    private final TestGlobalNoteRepository globalNoteRepository;

    /**
     * 构造测试用跨租户服务。
     *
     * @param tenantNoteRepository 租户隔离 note 仓库
     * @param globalNoteRepository 全局 note 仓库
     */
    CrossTenantTestService(TestTenantNoteRepository tenantNoteRepository, TestGlobalNoteRepository globalNoteRepository) {
        this.tenantNoteRepository = tenantNoteRepository;
        this.globalNoteRepository = globalNoteRepository;
    }

    /**
     * 在跨租户模式下清理所有测试数据。
     */
    @CrossTenant
    void deleteAllNotes() {
        tenantNoteRepository.deleteAll();
        globalNoteRepository.deleteAll();
    }

    /**
     * 在跨租户模式下查询所有 tenant-scoped note。
     *
     * @return 所有 note 列表
     */
    @CrossTenant
    List<TestTenantNotePO> listAllNotes() {
        return tenantNoteRepository.findAll();
    }

    /**
     * 在跨租户模式下统计所有 tenant-scoped note 数量。
     *
     * @return note 总数
     */
    @CrossTenant
    long countAllNotes() {
        return tenantNoteRepository.count();
    }

    /**
     * 在跨租户模式下判断指定 note 是否存在。
     *
     * @param id note id
     * @return 是否存在
     */
    @CrossTenant
    boolean noteExists(Long id) {
        return tenantNoteRepository.existsById(id);
    }

    /**
     * 在跨租户模式下按 id 查询 note。
     *
     * @param id note id
     * @return note；不存在则返回 {@code null}
     */
    @CrossTenant
    TestTenantNotePO getNote(Long id) {
        return tenantNoteRepository.findById(id).orElse(null);
    }

    /**
     * 在跨租户模式下为指定 tenant 写入 note（必须显式提供 entity tenantID）。
     *
     * @param tenantID 目标 tenantID
     * @param id note id
     * @param content note 内容
     */
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

    /**
     * 在跨租户模式下写入 note（不提供 entity tenantID），用于验证写入门禁。
     *
     * @param id note id
     * @param content note 内容
     */
    @CrossTenant
    void saveNoteWithoutTenantID(Long id, String content) {
        LocalDateTime now = LocalDateTime.now();

        TestTenantNotePO po = new TestTenantNotePO();
        po.setId(id);
        po.setContent(content);
        po.setCreateTime(now);
        po.setUpdateTime(now);
        tenantNoteRepository.save(po);
    }
}