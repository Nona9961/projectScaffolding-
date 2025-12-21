package com.nona.inf.persistence.tracking;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UnitOfWorkProvider 单元测试
 */
class UnitOfWorkProviderTest {

    // ========== 测试用类 ==========

    static class TestEntity {
        Long id;
        String name;
    }

    // ========== 构造测试 ==========

    @Test
    void shouldBuildWithProperties() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .defaultIdentifier("id")
                .valueTypePackage("com.example.vo")
                .build();

        UnitOfWorkProvider provider = new UnitOfWorkProvider(props);

        assertNotNull(provider);
        assertEquals(Set.of("com.example.vo"), provider.getValuePackages());
    }

    @Test
    void shouldBuildWithBuilder() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder()
                .withIdentifier(TestEntity.class, e -> e.id)
                .withValuePackage("com.example.vo")
                .build();

        assertNotNull(provider);
        assertTrue(provider.getExtractors().containsKey(TestEntity.class));
        assertTrue(provider.getValuePackages().contains("com.example.vo"));
    }

    @Test
    void shouldBuildFromPropertiesViaBuilder() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .valueTypePackage("com.example.types")
                .build();

        UnitOfWorkProvider provider = UnitOfWorkProvider.builder()
                .fromProperties(props)
                .withValuePackage("com.example.extra")
                .build();

        assertTrue(provider.getValuePackages().contains("com.example.types"));
        assertTrue(provider.getValuePackages().contains("com.example.extra"));
    }

    // ========== 提取器测试 ==========

    @Test
    void shouldRegisterIdentifierExtractor() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder()
                .withIdentifier(TestEntity.class, e -> e.id)
                .build();

        Map<Class<?>, Function<Object, Object>> extractors = provider.getExtractors();
        TestEntity entity = new TestEntity();
        entity.id = 123L;

        assertEquals(123L, extractors.get(TestEntity.class).apply(entity));
    }

    // ========== 值类型测试 ==========

    @Test
    void shouldRegisterValueType() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder()
                .withValueType(String.class)
                .build();

        assertTrue(provider.getValueTypes().contains(String.class));
    }

    // ========== 不可变视图测试 ==========

    @Test
    void shouldReturnUnmodifiableExtractors() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder().build();

        assertThrows(UnsupportedOperationException.class, () ->
                provider.getExtractors().put(Object.class, o -> o));
    }

    @Test
    void shouldReturnUnmodifiableValueTypes() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder().build();

        assertThrows(UnsupportedOperationException.class, () ->
                provider.getValueTypes().add(String.class));
    }

    @Test
    void shouldReturnUnmodifiableValuePackages() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder().build();

        assertThrows(UnsupportedOperationException.class, () ->
                provider.getValuePackages().add("com.example"));
    }

    // ========== 错误处理测试 ==========

    @Test
    void shouldThrowWhenValueTypeClassNotFound() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .valueType("com.nonexistent.Class")
                .build();

        assertThrows(IllegalStateException.class, () -> new UnitOfWorkProvider(props));
    }

    // ========== create() 测试需要 SPI，跳过 ==========
    // UnitOfWorkProvider.create() 依赖 ServiceLoader 加载 TrackingCapabilityProvider
    // 需要在集成测试中验证
}
