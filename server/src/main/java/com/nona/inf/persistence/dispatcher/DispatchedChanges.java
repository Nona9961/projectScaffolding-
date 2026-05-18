package com.nona.inf.persistence.dispatcher;

import com.nona.changeTracking.domain.model.changeset.FieldChange;
import com.nona.changeTracking.domain.model.changeset.ItemAddedChange;
import com.nona.changeTracking.domain.model.changeset.ItemRemovedChange;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 分发后的变更数据结构
 * <p>
 * 将 ChangeSet 分类为：
 * <ul>
 *     <li>主表字段变更</li>
 *     <li>子表/集合变更（按字段名分组）</li>
 * </ul>
 */
@Getter
@ScaffoldGenerated
public class DispatchedChanges {

    /**
     * 主表字段变更列表
     */
    private final List<FieldChange> mainTableChanges = new ArrayList<>();

    /**
     * 子表/集合变更，按字段名分组
     * <p>
     * key: 字段名（如 "items", "customer"）
     * value: 该字段的所有变更
     */
    private final Map<String, CollectionChanges> childTableChanges = new HashMap<>();

    /**
     * 添加主表字段变更
     */
    public void addMainTableChange(FieldChange change) {
        mainTableChanges.add(change);
    }

    /**
     * 获取或创建子表变更
     */
    public CollectionChanges getOrCreateCollectionChanges(String fieldName) {
        return childTableChanges.computeIfAbsent(fieldName, k -> new CollectionChanges());
    }

    /**
     * 子表/集合的变更数据
     */
    @Getter
    public static class CollectionChanges {
        /**
         * 新增项列表
         */
        private final List<ItemAddedChange> additions = new ArrayList<>();

        /**
         * 删除项列表
         */
        private final List<ItemRemovedChange> removals = new ArrayList<>();

        /**
         * 字段变更列表
         */
        private final List<FieldChange> fieldChanges = new ArrayList<>();

        public void addAddition(ItemAddedChange change) {
            additions.add(change);
        }

        public void addRemoval(ItemRemovedChange change) {
            removals.add(change);
        }

        public void addFieldChange(FieldChange change) {
            fieldChanges.add(change);
        }
    }

    /**
     * 检查是否为空（没有任何变更）
     */
    public boolean isEmpty() {
        return mainTableChanges.isEmpty() && childTableChanges.isEmpty();
    }
}
