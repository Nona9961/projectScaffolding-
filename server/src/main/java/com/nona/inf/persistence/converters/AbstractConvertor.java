package com.nona.inf.persistence.converters;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 抽象RDB结构转换器的基类，提供了获取泛型和空判断的辅助实现
 * <p>通过泛型反射机制自动获取Root和PO的类型信息，实现类型安全的转换操作</p>
 * <p>注意：要求子类必须显式指定泛型参数</p>
 * 即 {@code
 * static class B extends A<Integer> {
 * }}
 * <p>
 * {@code
 * static class A<T> extends AbstractConvertor<T, Long, Void> {}
 * }
 * <p>
 * 是不允许的
 *
 * @param <Root>  领域模型类型（DO）
 * @param <PO>    持久化对象类型（PO）
 * @param <Other> 转换过程需要的其他辅助参数类型
 * @author nona
 */
public abstract class AbstractConvertor<Root, PO, Other> implements RdbGeneralConvertor<Root, PO, Other> {

    protected volatile Class<Root> rootClassCache;
    protected volatile Class<PO> poClassCache;

    @Override
    public final Class<Root> rootClass() {
        if (rootClassCache == null) {
            synchronized (this) {
                if (rootClassCache == null) {
                    findGenericType();
                }
            }
        }
        return rootClassCache;
    }

    @Override
    public final Class<PO> poClass() {
        if (poClassCache == null) {
            synchronized (this) {
                if (poClassCache == null) {
                    findGenericType();
                }
            }
        }
        return poClassCache;
    }

    @Override
    public final PO convertToPO(Root root) {
        if (root == null) {
            return null;
        }
        return safedConvertToPO(root);
    }

    @Override
    public final Root convertToRoot(PO po, @Nullable Other other) {
        if (po == null) {
            return null;
        }
        return safedConvertToRoot(po, other);
    }

    protected abstract PO safedConvertToPO(@Nonnull Root root);

    protected abstract Root safedConvertToRoot(PO po, @Nullable Other other);

    /**
     * 通过反射获取泛型参数类型
     * <p>解析当前类的泛型超类，提取前两个泛型参数作为Root和PO的类型</p>
     * <p>注意：要求子类必须显式指定泛型参数</p>
     */
    @SuppressWarnings("unchecked")
    private void findGenericType() {
        Class<?> currentClass = this.getClass();
        while (currentClass != null && (rootClassCache == null || poClassCache == null)) {
            final Type genericSuperclass = currentClass.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType pt) {
                final Type[] actualTypeArguments = pt.getActualTypeArguments();
                if (actualTypeArguments.length >= 2) {
                    rootClassCache = (Class<Root>) actualTypeArguments[0];
                    poClassCache = (Class<PO>) actualTypeArguments[1];
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        if (rootClassCache.getTypeParameters().length > 0) {
            throw new IllegalStateException("Root class cannot be generic type: " + rootClassCache);
        }
        if (poClassCache.getTypeParameters().length > 0) {
            throw new IllegalStateException("PO class cannot be generic type: " + poClassCache);
        }
        if (rootClassCache == null || poClassCache == null) {
            throw new IllegalStateException("cannot find generic type");
        }
        if (rootClassCache.isInterface() || poClassCache.isInterface()) {
            throw new IllegalStateException("generic type must be class");
        }
    }
}
