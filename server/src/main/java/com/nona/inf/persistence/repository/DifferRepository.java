package com.nona.inf.persistence.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nona.changeTracking.domain.model.changeset.ChangeSet;
import com.nona.changeTracking.domain.model.unitofwork.UnitOfWork;
import com.nona.inf.context.ThreadContext;
import com.nona.inf.persistence.converters.RdbGeneralConvertor;
import com.nona.inf.persistence.po.BasePO;
import com.nona.inf.persistence.tracking.UnitOfWorkProvider;
import com.nona.persistence.BaseRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * 支持基于diff的仓储，集成 UnitOfWork 实现变更追踪
 *
 * @param <Root>  聚合根
 * @param <PO>    PO对象
 * @param <Other> 聚合根需要的其他对象，详见{@link RdbGeneralConvertor}
 * @see RdbGeneralConvertor
 */
@RequiredArgsConstructor
public abstract class DifferRepository<Root, PO extends BasePO, Other> implements BaseRepository<Long, Root> {

    private static final String UOW_KEY = "UNIT_OF_WORK";

    protected final ListCrudRepository<PO, Long> repository;
    protected final ThreadContext threadContext;
    protected final RdbGeneralConvertor<Root, PO, Other> convertor;
    protected final UnitOfWorkProvider unitOfWorkProvider;

    private final TypeReference<Root> rootType = new TypeReference<>() {};

    @Override
    public Root getByID(Long id) {
        final Optional<PO> poOptional = repository.findById(id);
        if (poOptional.isEmpty()) {
            return null;
        }
        final PO po = poOptional.get();
        final Root root = convertor.convertToRoot(po, getOther(po));
        if (root == null) {
            return null;
        }

        // 注册到 UnitOfWork 进行追踪
        getOrCreateUnitOfWork().registerClean(root);
        threadContext.saveSnapshot(id, root);
        return root;
    }

    /**
     * 获取other对象，默认实现为不需要other对象
     *
     * @param po po
     * @return The other object, or null if no other object is required.
     */
    @Nullable
    protected Other getOther(PO po) {
        return null;
    }

    protected abstract Long retrieveIDFromRoot(Root root);

    /**
     * 新增聚合根（子类实现）
     * <p>
     * 实现方式由子类决定：JDBC / JPA / 混合模式
     *
     * @param root 聚合根
     */
    protected abstract void doInsert(Root root);

    /**
     * 更新聚合根（子类实现）
     * <p>
     * 可选实现方式：
     * <ul>
     *     <li>遍历 changeSet.getLeafChanges() 手动生成 SQL (JDBC)</li>
     *     <li>从 ChangeSet 更新 PO，让 JPA 生成 SQL</li>
     *     <li>利用 changeSet.getAllChanges() 的树形结构递归处理</li>
     * </ul>
     *
     * @param root      当前聚合根状态
     * @param changeSet 变更集（包含完整树形结构）
     */
    protected abstract void doUpdate(Root root, ChangeSet changeSet);

    @Override
    public boolean save(Root root) {
        Objects.requireNonNull(root);
        final Long id = retrieveIDFromRoot(root);
        final UnitOfWork uow = getOrCreateUnitOfWork();

        if (!isTracked(id)) {
            // 新增：子类自由实现持久化策略
            doInsert(root);
            uow.registerClean(root);
            threadContext.saveSnapshot(id, root);
            return true;
        }

        // 计算变更
        final ChangeSet changeSet = uow.calculateChanges();
        if (changeSet.isEmpty()) {
            return false;
        }

        // 应用变更：子类自由实现持久化策略
        doUpdate(root, changeSet);

        // 重新注册快照
        uow.registerClean(root);
        threadContext.saveSnapshot(id, root);
        return true;
    }

    @Override
    public int delete(Root root) {
        return 0;
    }

    @Override
    public int deleteByID(Long id) {
        return 0;
    }

    /**
     * 检查是否已被追踪
     */
    private boolean isTracked(Long id) {
        return threadContext.getSnapshot(id, rootType) != null;
    }

    /**
     * 获取或创建 UnitOfWork（请求级别单例）
     */
    protected UnitOfWork getOrCreateUnitOfWork() {
        UnitOfWork uow = threadContext.getAttribute(UOW_KEY);
        if (uow == null) {
            uow = unitOfWorkProvider.create();
            threadContext.setAttribute(UOW_KEY, uow);
        }
        return uow;
    }
}
