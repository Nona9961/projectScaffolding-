package com.nona.inf.persistence.tracking;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdentifierExtractorBuilder 单元测试
 */
class IdentifierExtractorBuilderTest {

    // ========== 测试用类 ==========

    static class TestEntity {
        private Long id;
        private String name;

        public TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }

    static class TestEntityWithUuid {
        private String uuid;

        public TestEntityWithUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getUuid() { return uuid; }
    }

    // ========== 默认提取器测试 ==========

    @Test
    void shouldExtractByDefaultField() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .defaultIdentifier("id")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);

        Function<Object, Object> extractor = builder.getDefaultExtractor();
        TestEntity entity = new TestEntity(123L, "test");

        assertEquals(123L, extractor.apply(entity));
    }

    @Test
    void shouldFallbackToIdentityHashCodeWhenFieldNotFound() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .defaultIdentifier("nonExistentField")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);

        Function<Object, Object> extractor = builder.getDefaultExtractor();
        TestEntity entity = new TestEntity(123L, "test");

        // 回退到 identityHashCode
        assertEquals(System.identityHashCode(entity), extractor.apply(entity));
    }

    // ========== 字段覆盖配置测试 ==========

    @Test
    void shouldBuildExtractorFromFieldOverride() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .identifierOverride(TestEntityWithUuid.class.getName(), "uuid")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);

        Map<Class<?>, Function<Object, Object>> extractors = builder.build();
        TestEntityWithUuid entity = new TestEntityWithUuid("abc-123");

        assertTrue(extractors.containsKey(TestEntityWithUuid.class));
        assertEquals("abc-123", extractors.get(TestEntityWithUuid.class).apply(entity));
    }

    // ========== 方法配置测试 ==========

    @Test
    void shouldBuildExtractorFromMethodConfig() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .identifierMethod(TestEntity.class.getName(), "getName")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);

        Map<Class<?>, Function<Object, Object>> extractors = builder.build();
        TestEntity entity = new TestEntity(123L, "testName");

        assertTrue(extractors.containsKey(TestEntity.class));
        assertEquals("testName", extractors.get(TestEntity.class).apply(entity));
    }

    @Test
    void shouldMethodConfigOverrideFieldConfig() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .identifierOverride(TestEntity.class.getName(), "id")
                .identifierMethod(TestEntity.class.getName(), "getName")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);

        Map<Class<?>, Function<Object, Object>> extractors = builder.build();
        TestEntity entity = new TestEntity(123L, "testName");

        // 方法配置优先
        assertEquals("testName", extractors.get(TestEntity.class).apply(entity));
    }

    // ========== 错误处理测试 ==========

    @Test
    void shouldThrowWhenClassNotFound() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .identifierOverride("com.nonexistent.Class", "id")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);

        assertThrows(IllegalStateException.class, builder::build);
    }

    // ========== getExtractorFor 测试 ==========

    @Test
    void shouldGetExtractorForRegisteredClass() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .identifierOverride(TestEntity.class.getName(), "name")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);
        Map<Class<?>, Function<Object, Object>> extractors = builder.build();

        Function<Object, Object> extractor = builder.getExtractorFor(extractors, TestEntity.class);
        TestEntity entity = new TestEntity(123L, "testName");

        assertEquals("testName", extractor.apply(entity));
    }

    @Test
    void shouldFallbackToDefaultFieldForUnregisteredClass() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .defaultIdentifier("id")
                .build();
        IdentifierExtractorBuilder builder = new IdentifierExtractorBuilder(props);
        Map<Class<?>, Function<Object, Object>> extractors = builder.build();

        Function<Object, Object> extractor = builder.getExtractorFor(extractors, TestEntity.class);
        TestEntity entity = new TestEntity(456L, "test");

        assertEquals(456L, extractor.apply(entity));
    }
}
