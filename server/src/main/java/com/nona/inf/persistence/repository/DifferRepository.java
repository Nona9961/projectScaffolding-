package com.nona.inf.persistence.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nona.inf.context.ThreadContext;
import com.nona.inf.persistence.converters.RdbGeneralConvertor;
import com.nona.inf.persistence.diff.DifferAnalyzer;
import com.nona.inf.persistence.po.BasePO;
import com.nona.persisitence.BaseRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * 支持基于diff的仓储，因为JPA的快照是PO对象，而diff应该从比较root对象快照而来，所以不能使用hibernate的快照
 *
 * @param <Root>  聚合根
 * @param <PO>    PO对象
 * @param <Other> 聚合根需要的其他对象，详见{@link RdbGeneralConvertor}
 * @see RdbGeneralConvertor
 * @deprecated 暂时没有实现好这个类，先别用
 */
@RequiredArgsConstructor
@Deprecated
public abstract class DifferRepository<Root, PO extends BasePO, Other> implements BaseRepository<Long, Root> {

    protected final ListCrudRepository<PO, Long> repository;
    private final Javers javers;
    protected final ThreadContext threadContext;
    private final TypeReference<Root> rootType = new TypeReference<>() {
    };
    protected final RdbGeneralConvertor<Root, PO, Other> convertor;

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

    @Override
    public boolean save(Root root) {
        Objects.requireNonNull(root);
        final Long id = retrieveIDFromRoot(root);
        final Root snapshot = threadContext.getSnapshot(id, rootType);
        if (snapshot == null) {
            // insert
            final PO po = convertor.convertToPO(root);
            repository.save(po);
            threadContext.saveSnapshot(id, root);
            return true;
        }
        final Diff diffs = javers.compare(snapshot, root);
        if (!diffs.hasChanges()) {
            return true;
        }
        final DifferAnalyzer differAnalyzer = DifferAnalyzer.analyzeFromDiff(diffs);
        threadContext.saveSnapshot(id, root);
        return false;
    }

    @Override
    public int delete(Root root) {
        return 0;
    }

    @Override
    public int deleteByID(Long id) {
        return 0;
    }

}
