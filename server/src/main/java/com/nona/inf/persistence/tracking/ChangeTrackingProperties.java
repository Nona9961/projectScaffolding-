package com.nona.inf.persistence.tracking;

import lombok.Data;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 变更追踪配置属性类
 * <p>
 * 支持从 YAML 或 Properties 文件加载配置，提供：
 * <ul>
 *     <li>全局默认标识符字段名</li>
 *     <li>按类型覆盖标识符配置</li>
 *     <li>值类型包和类配置</li>
 * </ul>
 */
@Data
public class ChangeTrackingProperties {

    /**
     * 全局默认标识符字段名
     * 大多数实体都使用 "id" 作为标识符字段
     */
    private String defaultIdentifier = "id";

    /**
     * 值类型包路径列表
     * 这些包下的类将被视为值类型，不会递归展开其字段
     */
    private List<String> valueTypePackages = new ArrayList<>();

    /**
     * 值类型类名列表（全限定名）
     * 这些类将被视为值类型
     */
    private List<String> valueTypes = new ArrayList<>();

    /**
     * 类型 -> 标识符字段名的覆盖配置
     * key: 类全限定名
     * value: 标识符字段名
     */
    private Map<String, String> identifierOverrides = new HashMap<>();

    /**
     * 类型 -> 标识符方法名的配置（优先于字段）
     * key: 类全限定名
     * value: 方法名（无参方法）
     */
    private Map<String, String> identifierMethods = new HashMap<>();

    /**
     * 创建默认配置实例
     */
    public static ChangeTrackingProperties defaults() {
        return new ChangeTrackingProperties();
    }

    /**
     * 从 Properties 文件加载配置
     *
     * @param inputStream Properties 文件输入流
     * @return 配置实例
     */
    public static ChangeTrackingProperties loadFromProperties(InputStream inputStream) throws IOException {
        Properties props = new Properties();
        props.load(inputStream);
        return fromProperties(props);
    }

    /**
     * 从 Properties 对象构建配置
     * <p>
     * 支持的配置项：
     * <pre>
     * change-tracking.default-identifier=id
     * change-tracking.value-type-packages=com.example.vo,com.example.types
     * change-tracking.value-types=com.example.Money,com.example.Address
     * change-tracking.identifier-overrides.com.example.OrderItem=productId
     * change-tracking.identifier-methods.com.example.Entity=getBusinessKey
     * </pre>
     */
    public static ChangeTrackingProperties fromProperties(Properties props) {
        ChangeTrackingProperties config = new ChangeTrackingProperties();
        String prefix = "change-tracking.";

        // 默认标识符
        String defaultId = props.getProperty(prefix + "default-identifier");
        if (defaultId != null && !defaultId.isBlank()) {
            config.setDefaultIdentifier(defaultId.trim());
        }

        // 值类型包（逗号分隔）
        String packages = props.getProperty(prefix + "value-type-packages");
        if (packages != null && !packages.isBlank()) {
            config.setValueTypePackages(parseCommaSeparated(packages));
        }

        // 值类型类（逗号分隔）
        String types = props.getProperty(prefix + "value-types");
        if (types != null && !types.isBlank()) {
            config.setValueTypes(parseCommaSeparated(types));
        }

        // 标识符覆盖配置
        String overridePrefix = prefix + "identifier-overrides.";
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(overridePrefix)) {
                String className = key.substring(overridePrefix.length());
                String fieldName = props.getProperty(key);
                if (fieldName != null && !fieldName.isBlank()) {
                    config.getIdentifierOverrides().put(className, fieldName.trim());
                }
            }
        }

        // 标识符方法配置
        String methodPrefix = prefix + "identifier-methods.";
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(methodPrefix)) {
                String className = key.substring(methodPrefix.length());
                String methodName = props.getProperty(key);
                if (methodName != null && !methodName.isBlank()) {
                    config.getIdentifierMethods().put(className, methodName.trim());
                }
            }
        }

        return config;
    }

    /**
     * 使用 Builder 模式构建配置
     */
    public static Builder builder() {
        return new Builder();
    }

    private static List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Builder 类，支持链式配置
     */
    public static class Builder {
        private final ChangeTrackingProperties properties = new ChangeTrackingProperties();

        public Builder defaultIdentifier(String fieldName) {
            properties.setDefaultIdentifier(fieldName);
            return this;
        }

        public Builder valueTypePackage(String packageName) {
            properties.getValueTypePackages().add(packageName);
            return this;
        }

        public Builder valueTypePackages(List<String> packages) {
            properties.setValueTypePackages(new ArrayList<>(packages));
            return this;
        }

        public Builder valueType(String className) {
            properties.getValueTypes().add(className);
            return this;
        }

        public Builder valueTypes(List<String> classNames) {
            properties.setValueTypes(new ArrayList<>(classNames));
            return this;
        }

        public Builder identifierOverride(String className, String fieldName) {
            properties.getIdentifierOverrides().put(className, fieldName);
            return this;
        }

        public Builder identifierOverrides(Map<String, String> overrides) {
            properties.setIdentifierOverrides(new HashMap<>(overrides));
            return this;
        }

        public Builder identifierMethod(String className, String methodName) {
            properties.getIdentifierMethods().put(className, methodName);
            return this;
        }

        public Builder identifierMethods(Map<String, String> methods) {
            properties.setIdentifierMethods(new HashMap<>(methods));
            return this;
        }

        public ChangeTrackingProperties build() {
            return properties;
        }
    }
}
