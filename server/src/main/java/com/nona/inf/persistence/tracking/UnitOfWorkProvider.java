package com.nona.inf.persistence.tracking;

import com.nona.changeTracking.domain.model.unitofwork.UnitOfWork;
import com.nona.changeTracking.spi.TrackingCapabilityProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;

/**
 * UnitOfWork 提供者
 * <p>
 * 根据 {@link ChangeTrackingProperties} 配置创建 {@link UnitOfWork} 实例。
 * 每次调用 {@link #create()} 返回一个新的 UnitOfWork 实例。
 * <p>
 * 该类封装了 change-tracking 库的配置细节，对外提供简单的工厂方法。
 */
@Slf4j
public class UnitOfWorkProvider {

    private final Map<Class<?>, Function<Object, Object>> extractors;
    private final Set<Class<?>> valueTypes;
    private final Set<String> valuePackages;

    /**
     * 缓存的 TrackingCapabilityProvider 类，避免每次 create() 都进行 SPI 查找
     */
    private final Class<? extends TrackingCapabilityProvider> providerClass;

    /**
     * 从配置属性创建提供者
     *
     * @param properties 配置属性
     */
    public UnitOfWorkProvider(ChangeTrackingProperties properties) {
        Objects.requireNonNull(properties, "properties cannot be null");

        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(properties);
        this.extractors = builder.build();
        this.valueTypes = resolveValueTypes(properties);
        this.valuePackages = new HashSet<>(properties.getValueTypePackages());
        this.providerClass = discoverProviderClass();
    }

    /**
     * 使用预构建的配置创建提供者
     *
     * @param extractors    标识符提取器映射
     * @param valueTypes    值类型集合
     * @param valuePackages 值类型包集合
     */
    public UnitOfWorkProvider(
            Map<Class<?>, Function<Object, Object>> extractors,
            Set<Class<?>> valueTypes,
            Set<String> valuePackages) {
        this.extractors = new HashMap<>(Objects.requireNonNull(extractors));
        this.valueTypes = new HashSet<>(Objects.requireNonNull(valueTypes));
        this.valuePackages = new HashSet<>(Objects.requireNonNull(valuePackages));
        this.providerClass = discoverProviderClass();
    }

    /**
     * 创建新的 UnitOfWork 实例
     * <p>
     * 每次调用返回一个独立的实例，适用于请求级别的生命周期管理。
     *
     * @return 配置好的 UnitOfWork 实例
     */
    public UnitOfWork create() {
        // 使用缓存的 provider 类创建新实例
        TrackingCapabilityProvider provider = createProviderInstance();

        // 注册所有标识符提取器
        for (Map.Entry<Class<?>, Function<Object, Object>> entry : extractors.entrySet()) {
            registerIdentifier(provider, entry.getKey(), entry.getValue());
        }

        // 注册值类型
        for (Class<?> valueType : valueTypes) {
            provider.withValueType(valueType);
        }

        // 注册值类型包
        for (String packageName : valuePackages) {
            provider.withValuePackage(packageName);
        }

        return new UnitOfWork(provider.create());
    }

    /**
     * 通过 SPI 发现 TrackingCapabilityProvider 实现类
     *
     * @return Provider 实现类
     * @throws IllegalStateException 如果没有找到实现
     */
    private Class<? extends TrackingCapabilityProvider> discoverProviderClass() {
        ServiceLoader<TrackingCapabilityProvider> loader = ServiceLoader.load(TrackingCapabilityProvider.class);
        TrackingCapabilityProvider provider = loader.findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No TrackingCapabilityProvider found. Ensure change-tracking-core is on the classpath."));
        return provider.getClass();
    }

    /**
     * 创建新的 Provider 实例
     *
     * @return 新的 Provider 实例
     */
    private TrackingCapabilityProvider createProviderInstance() {
        try {
            return providerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create TrackingCapabilityProvider instance", e);
        }
    }

    /**
     * 注册标识符提取器到 Provider
     * <p>
     * 使用 unchecked cast 是因为 TrackingCapabilityProvider 的 API 设计需要泛型类型参数
     */
    @SuppressWarnings("unchecked")
    private <T> void registerIdentifier(
            TrackingCapabilityProvider provider,
            Class<?> clazz,
            Function<Object, Object> extractor) {
        provider.withIdentifier((Class<T>) clazz, extractor::apply);
    }

    /**
     * 从配置解析值类型
     *
     * @throws IllegalStateException 如果配置的类不存在
     */
    private Set<Class<?>> resolveValueTypes(ChangeTrackingProperties properties) {
        Set<Class<?>> types = new HashSet<>();
        for (String className : properties.getValueTypes()) {
            try {
                types.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        String.format("Value type class '%s' not found. " +
                                "Please check your change-tracking configuration.", className), e);
            }
        }
        return types;
    }

    /**
     * 获取当前配置的提取器映射（只读视图）
     *
     * @return 提取器映射的不可变视图
     */
    public Map<Class<?>, Function<Object, Object>> getExtractors() {
        return Collections.unmodifiableMap(extractors);
    }

    /**
     * 获取当前配置的值类型（只读视图）
     *
     * @return 值类型集合的不可变视图
     */
    public Set<Class<?>> getValueTypes() {
        return Collections.unmodifiableSet(valueTypes);
    }

    /**
     * 获取当前配置的值类型包（只读视图）
     *
     * @return 值类型包集合的不可变视图
     */
    public Set<String> getValuePackages() {
        return Collections.unmodifiableSet(valuePackages);
    }

    /**
     * 使用 Builder 模式创建提供者
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private final Map<Class<?>, Function<Object, Object>> extractors = new HashMap<>();
        private final Set<Class<?>> valueTypes = new HashSet<>();
        private final Set<String> valuePackages = new HashSet<>();

        private Builder() {}

        /**
         * 从配置属性加载
         *
         * @throws IllegalStateException 如果配置的类不存在
         */
        public Builder fromProperties(ChangeTrackingProperties properties) {
            IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(properties);
            this.extractors.putAll(builder.build());
            properties.getValueTypes().forEach(className -> {
                try {
                    this.valueTypes.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(
                            String.format("Value type class '%s' not found. " +
                                    "Please check your change-tracking configuration.", className), e);
                }
            });
            this.valuePackages.addAll(properties.getValueTypePackages());
            return this;
        }

        /**
         * 添加标识符提取器
         */
        public <T> Builder withIdentifier(Class<T> clazz, Function<T, Object> extractor) {
            this.extractors.put(clazz, obj -> extractor.apply((T) obj));
            return this;
        }

        /**
         * 添加值类型
         */
        public Builder withValueType(Class<?> clazz) {
            this.valueTypes.add(clazz);
            return this;
        }

        /**
         * 添加值类型包
         */
        public Builder withValuePackage(String packageName) {
            this.valuePackages.add(packageName);
            return this;
        }

        /**
         * 构建提供者
         */
        public UnitOfWorkProvider build() {
            return new UnitOfWorkProvider(extractors, valueTypes, valuePackages);
        }
    }
}
