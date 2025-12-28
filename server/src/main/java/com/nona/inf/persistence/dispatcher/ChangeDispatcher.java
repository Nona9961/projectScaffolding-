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
 * 使用 {@link ChangeSet#getLeafChanges()} 获取叶子变更，通过路径解析分类。
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
        final DispatchedChanges result = new DispatchedChanges();
        final Map<String, PoConverter<?, ?>> allConverters = converterRegistry.getAllConverters();

        for (final Change change : changeSet.getLeafChanges()) {
            final String fieldName = extractDeepestCollectionField(change.path(), allConverters);

            if (fieldName != null && allConverters.containsKey(fieldName)) {
                categorizeChildChange(change, fieldName, result);
            } else {
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
    private void categorizeChildChange(Change change, String fieldName, DispatchedChanges result) {
        final DispatchedChanges.CollectionChanges collectionChanges = result.getOrCreateCollectionChanges(fieldName);

        switch (change) {
            case ItemAddedChange iac -> collectionChanges.addAddition(iac);
            case ItemRemovedChange irc -> collectionChanges.addRemoval(irc);
            case FieldChange fc -> collectionChanges.addFieldChange(fc);
            default -> {}
        }
    }

    /**
     * 从路径中提取最深层的、已注册转换器的集合字段名
     * <p>
     * 示例（假设 items, subItems, specs 都已注册）：
     * <ul>
     *     <li>items → items (ItemAddedChange/ItemRemovedChange 的路径)</li>
     *     <li>items[101] → items</li>
     *     <li>items[101].quantity → items</li>
     *     <li>items[101].subItems[201] → subItems</li>
     *     <li>items[101].subItems[201].name → subItems</li>
     *     <li>items[101].subItems[201].specs[color] → specs</li>
     *     <li>status → null (主表字段)</li>
     * </ul>
     */
    private String extractDeepestCollectionField(String path, Map<String, PoConverter<?, ?>> converters) {
        String deepestField = null;
        int i = 0;
        while (i < path.length()) {
            final int fieldStart = i;
            while (i < path.length() && path.charAt(i) != '[' && path.charAt(i) != '.') {
                i++;
            }
            final String fieldName = path.substring(fieldStart, i);

            if (i < path.length() && path.charAt(i) == '[') {
                // 这是一个集合字段，检查是否已注册转换器
                if (converters.containsKey(fieldName)) {
                    deepestField = fieldName;
                }
                // 跳过 [identifier]
                while (i < path.length() && path.charAt(i) != ']') {
                    i++;
                }
                if (i < path.length()) i++;
            } else if (i == path.length() || path.charAt(i) == '.') {
                // 路径结束或遇到点号，检查是否是已注册的集合字段
                // 这处理 ItemAddedChange/ItemRemovedChange 的路径格式（如 "items"）
                if (converters.containsKey(fieldName)) {
                    deepestField = fieldName;
                }
            }

            if (i < path.length() && path.charAt(i) == '.') {
                i++;
            } else {
                break;
            }
        }

        return deepestField;
    }
}
