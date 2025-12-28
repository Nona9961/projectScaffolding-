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
 * 利用 {@link ChangeSet#getLeafChanges()} 和 {@link Change#collectionFieldName()} 进行分类。
 */
@RequiredArgsConstructor
public class ChangeDispatcher {

    private final ConverterRegistry converterRegistry;

    public DispatchedChanges dispatch(ChangeSet changeSet, Class<?> rootClass) {
        final DispatchedChanges result = new DispatchedChanges();
        final Map<String, PoConverter<?, ?>> converters = converterRegistry.getAllConverters();

        for (final Change change : changeSet.getLeafChanges()) {
            final String collectionField = change.collectionFieldName();

            if (collectionField != null && converters.containsKey(collectionField)) {
                categorizeChildChange(change, collectionField, result);
            } else if (change instanceof FieldChange fc) {
                result.addMainTableChange(fc);
            }
        }

        return result;
    }

    private void categorizeChildChange(Change change, String fieldName, DispatchedChanges result) {
        final DispatchedChanges.CollectionChanges collectionChanges = result.getOrCreateCollectionChanges(fieldName);

        switch (change) {
            case ItemAddedChange iac -> collectionChanges.addAddition(iac);
            case ItemRemovedChange irc -> collectionChanges.addRemoval(irc);
            case FieldChange fc -> collectionChanges.addFieldChange(fc);
            default -> {}
        }
    }
}
