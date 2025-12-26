package com.nona.inf.persistence.dispatcher;

import com.nona.changeTracking.domain.model.changeset.*;
import com.nona.inf.persistence.converters.ConverterRegistry;
import com.nona.inf.persistence.converters.PoConverter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 变更分发器
 * <p>
 * 职责：将 ChangeSet 分类为主表变更和子表变更，返回结构化的数据。
 * <p>
 * <b>不关心如何处理变更，只负责分类。</b>
 */
@RequiredArgsConstructor
public class ChangeDispatcher {

    private final ConverterRegistry converterRegistry;

    /**
     * 分发变更
     *
     * @param changeSet 变更集
     * @param rootClass 聚合根类型
     * @return 分类后的变更数据
     */
    public DispatchedChanges dispatch(ChangeSet changeSet, Class<?> rootClass) {
        DispatchedChanges result = new DispatchedChanges();

        // 获取子表映射
        Map<String, PoConverter<?, ?>> childConverters = converterRegistry.getChildConverters(rootClass);

        // 遍历所有变更，进行分类
        for (Change change : changeSet.getLeafChanges()) {
            String path = change.path();
            String rootField = extractRootFieldName(path);

            if (childConverters.containsKey(rootField)) {
                // 子表变更
                categorizeChildChange(change, rootField, result);
            } else {
                // 主表变更
                if (change instanceof FieldChange fc) {
                    result.addMainTableChange(fc);
                }
            }
        }

        return result;
    }

    /**
     * 分类子表变更
     */
    private void categorizeChildChange(Change change, String rootField, DispatchedChanges result) {
        DispatchedChanges.CollectionChanges collectionChanges = result.getOrCreateCollectionChanges(rootField);

        switch (change) {
            case ItemAddedChange iac -> collectionChanges.addAddition(iac);
            case ItemRemovedChange irc -> collectionChanges.addRemoval(irc);
            case FieldChange fc -> collectionChanges.addFieldChange(fc);
            default -> {}
        }
    }

    /**
     * 提取路径的根字段名
     * <p>
     * 示例：items[101].quantity → items
     */
    private String extractRootFieldName(String path) {
        int bracketIdx = path.indexOf('[');
        int dotIdx = path.indexOf('.');
        if (bracketIdx == -1 && dotIdx == -1) return path;
        if (bracketIdx == -1) return path.substring(0, dotIdx);
        if (dotIdx == -1) return path.substring(0, bracketIdx);
        return path.substring(0, Math.min(bracketIdx, dotIdx));
    }
}
