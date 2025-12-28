package com.nona.inf.persistence.converters;

import com.nona.inf.persistence.po.BasePO;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 转换器注册中心
 * <p>
 * 支持：
 * <ul>
 *     <li>简单转换器注册与查询</li>
 *     <li>组合转换器注册与查询</li>
 *     <li>子表自动扫描（基于已注册的 PoConverter）</li>
 *     <li>手动声明覆盖自动扫描</li>
 * </ul>
 */
public class ConverterRegistry {

    private final Map<Class<?>, PoConverter<?, ?>> simpleConverters = new ConcurrentHashMap<>();
    private final Map<Class<?>, CompositePoConverter<?, ?>> compositeConverters = new ConcurrentHashMap<>();

    /**
     * 注册简单转换器
     */
    public <DO, PO> void register(PoConverter<DO, PO> converter) {
        simpleConverters.put(converter.domainClass(), converter);
    }

    /**
     * 注册组合转换器
     */
    public <Root, MainPO extends BasePO> void register(CompositePoConverter<Root, MainPO> converter) {
        compositeConverters.put(converter.rootClass(), converter);
    }

    /**
     * 获取简单转换器
     */
    @SuppressWarnings("unchecked")
    public <DO, PO> Optional<PoConverter<DO, PO>> getConverter(Class<DO> domainClass) {
        return Optional.ofNullable((PoConverter<DO, PO>) simpleConverters.get(domainClass));
    }

    /**
     * 获取组合转换器
     */
    @SuppressWarnings("unchecked")
    public <Root, MainPO extends BasePO> Optional<CompositePoConverter<Root, MainPO>>
            getCompositeConverter(Class<Root> rootClass) {
        return Optional.ofNullable((CompositePoConverter<Root, MainPO>) compositeConverters.get(rootClass));
    }

    /**
     * 获取聚合根的子实体映射（自动扫描 + 手动声明合并）
     *
     * @param rootClass 聚合根类型
     * @return 属性路径 -> 转换器 的映射
     */
    public Map<String, PoConverter<?, ?>> getChildConverters(Class<?> rootClass) {
        // 1. 自动扫描
        Map<String, PoConverter<?, ?>> result = scanChildConverters(rootClass);

        // 2. 合并手动声明（覆盖 + 新增）
        CompositePoConverter<?, ?> converter = compositeConverters.get(rootClass);
        if (converter != null) {
            result.putAll(converter.declareChildConverters());
        }

        return result;
    }

    /**
     * 获取所有字段名到转换器的映射（递归扫描所有已注册的领域类）
     *
     * @return 字段名 -> 转换器 的映射
     */
    public Map<String, PoConverter<?, ?>> getAllConverters() {
        final Map<String, PoConverter<?, ?>> result = new HashMap<>();

        // 扫描所有聚合根类
        for (final CompositePoConverter<?, ?> converter : compositeConverters.values()) {
            scanFieldsRecursively(converter.rootClass(), result);
        }

        // 扫描所有已注册的简单领域类
        for (final PoConverter<?, ?> converter : simpleConverters.values()) {
            scanFieldsRecursively(converter.domainClass(), result);
        }

        return result;
    }

    /**
     * 递归扫描类的字段，建立字段名到转换器的映射
     */
    private void scanFieldsRecursively(Class<?> clazz, Map<String, PoConverter<?, ?>> result) {
        for (final Field field : getAllFields(clazz)) {
            final Class<?> elementType = extractElementType(field);
            if (elementType != null) {
                final PoConverter<?, ?> converter = simpleConverters.get(elementType);
                if (converter != null) {
                    result.put(field.getName(), converter);
                    // 递归扫描子类
                    scanFieldsRecursively(elementType, result);
                }
            }
        }
    }

    /**
     * 自动扫描聚合根类的字段，识别子表
     */
    private Map<String, PoConverter<?, ?>> scanChildConverters(Class<?> rootClass) {
        Map<String, PoConverter<?, ?>> result = new HashMap<>();
        for (Field field : getAllFields(rootClass)) {
            Class<?> elementType = extractElementType(field);
            if (elementType != null) {
                PoConverter<?, ?> converter = simpleConverters.get(elementType);
                if (converter != null) {
                    result.put(field.getName(), converter);
                }
            }
        }
        return result;
    }

    /**
     * 获取类的所有字段（包括父类）
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * 提取字段的元素类型
     * <p>
     * - Collection&lt;T&gt; → T
     * - 单个对象 → 字段类型
     */
    private Class<?> extractElementType(Field field) {
        Class<?> fieldType = field.getType();

        // 集合类型：提取泛型参数
        if (Collection.class.isAssignableFrom(fieldType)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> clazz) {
                    return clazz;
                }
            }
            return null;  // 泛型擦除，无法识别
        }

        // 非集合类型：直接返回字段类型
        return fieldType;
    }
}
