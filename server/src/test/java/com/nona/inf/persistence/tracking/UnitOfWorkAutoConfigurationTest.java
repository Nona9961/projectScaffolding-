package com.nona.inf.persistence.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UnitOfWork自动配置测试
 */
class UnitOfWorkAutoConfigurationTest {

    static final class TestEntity {
        Long id;
        String name;

        TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        Long getId() {
            return id;
        }
    }

    static final class TestValueObject {
        private final String value;

        TestValueObject(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    /**
     * 场景1：用户未定义Bean时自动创建
     */
    @Test
    void shouldCreateProviderWhenNoUserDefined() {
        final ChangeTrackingProperties props = new ChangeTrackingProperties();
        props.setDefaultIdentifier("id");
        props.getValueTypePackages().add("com.example.vo");

        final UnitOfWorkProvider provider = UnitOfWorkAutoConfiguration
                .createProvider(props);

        assertNotNull(provider);
    }

    /**
     * 场景2：从配置加载值对象包
     */
    @Test
    void shouldLoadValuePackagesFromProperties() {
        final ChangeTrackingProperties props = new ChangeTrackingProperties();
        props.setDefaultIdentifier("id");
        props.getValueTypePackages().add("com.example.vo");

        final UnitOfWorkProvider provider = UnitOfWorkAutoConfiguration
                .createProvider(props);

        assertTrue(provider.getValuePackages().contains("com.example.vo"));
    }

    /**
     * 场景3：从配置加载值对象类
     */
    @Test
    void shouldLoadValueTypesFromProperties() {
        final ChangeTrackingProperties props = new ChangeTrackingProperties();
        props.setDefaultIdentifier("id");
        props.getValueTypes().add(TestValueObject.class.getName());

        final UnitOfWorkProvider provider = UnitOfWorkAutoConfiguration
                .createProvider(props);

        assertTrue(provider.getValueTypes().contains(TestValueObject.class));
    }

    /**
     * 场景4：从配置加载标识符提取器
     */
    @Test
    void shouldLoadIdentifierExtractorsFromProperties() {
        final ChangeTrackingProperties props = new ChangeTrackingProperties();
        props.setDefaultIdentifier("id");
        props.getIdentifierMethods().put(
                TestEntity.class.getName(), "getId");

        final UnitOfWorkProvider provider = UnitOfWorkAutoConfiguration
                .createProvider(props);
        final TestEntity entity = new TestEntity(123L, "test");

        assertNotNull(provider.getExtractors().get(TestEntity.class));
        assertEquals(123L, provider.getExtractors()
                .get(TestEntity.class).apply(entity));
    }
}
