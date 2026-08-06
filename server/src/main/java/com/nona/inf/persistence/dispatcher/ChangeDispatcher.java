package com.nona.inf.persistence.dispatcher;

import com.nona.changeTracking.domain.model.changeset.*;
import com.nona.inf.persistence.converters.ConverterRegistry;
import com.nona.inf.persistence.converters.PoConverter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 变更分发器
 * <p>
 * 职责：将 ChangeSet 分类为主表变更和子表变更，返回结构化的数据。
 * <p>
 * 利用 {@link ChangeSet#getLeafChanges()} 和 {@link Change#collectionFieldName()} 进行分类。
 */
@RequiredArgsConstructor
@ScaffoldGenerated
public class ChangeDispatcher {

    private final ConverterRegistry converterRegistry;

    public DispatchedChanges dispatch(ChangeSet changeSet, Class<?> rootClass) {
        final DispatchedChanges result = new DispatchedChanges();
        final Map<String, PoConverter<?, ?>> converters = converterRegistry.getAllConverters();

        for (final Change change : changeSet.getLeafChanges()) {
            final String collectionField = change.collectionFieldName();

            if (collectionField != null && converters.containsKey(collectionField)) {
                categorizeChildChange(change, collectionField, result);
            } else if (change instanceof ValueChange vc) {
                result.addMainTableChange(vc);
            } else if (change instanceof ObjectFieldChange ofc) {
                // 对象/集合字段整体替换（跨类型变化）：ValueNode 承载，无业务值
                result.addMainTableChange(ofc);
            }
        }

        return result;
    }

    private void categorizeChildChange(Change change, String fieldName, DispatchedChanges result) {
        final DispatchedChanges.CollectionChanges collectionChanges = result.getOrCreateCollectionChanges(fieldName);

        switch (change) {
            case ItemAddedChange iac -> collectionChanges.addAddition(iac);
            case ItemRemovedChange irc -> collectionChanges.addRemoval(irc);
            case ValueChange vc -> collectionChanges.addFieldChange(vc);
            case ObjectFieldChange ofc -> collectionChanges.addFieldChange(ofc);
            default -> {}
        }
    }
}
