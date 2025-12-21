package com.nona.inf.persistence.tracking;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChangeTrackingProperties 单元测试
 */
class ChangeTrackingPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        ChangeTrackingProperties props = new ChangeTrackingProperties();

        assertEquals("id", props.getDefaultIdentifier());
        assertTrue(props.getValueTypePackages().isEmpty());
        assertTrue(props.getValueTypes().isEmpty());
        assertTrue(props.getIdentifierOverrides().isEmpty());
        assertTrue(props.getIdentifierMethods().isEmpty());
    }

    @Test
    void shouldBuildWithBuilder() {
        ChangeTrackingProperties props = ChangeTrackingProperties.builder()
                .defaultIdentifier("uuid")
                .valueTypePackage("com.example.vo")
                .valueType("com.example.Money")
                .identifierOverride("com.example.Order", "orderId")
                .identifierMethod("com.example.Item", "getItemId")
                .build();

        assertEquals("uuid", props.getDefaultIdentifier());
        assertEquals(List.of("com.example.vo"), props.getValueTypePackages());
        assertEquals(List.of("com.example.Money"), props.getValueTypes());
        assertEquals(Map.of("com.example.Order", "orderId"), props.getIdentifierOverrides());
        assertEquals(Map.of("com.example.Item", "getItemId"), props.getIdentifierMethods());
    }

    @Test
    void shouldLoadFromProperties() throws IOException {
        String content = """
                change-tracking.default-identifier=uuid
                change-tracking.value-type-packages=com.example.vo,com.example.types
                change-tracking.value-types=com.example.Money
                change-tracking.identifier-overrides.com.example.Order=orderId
                change-tracking.identifier-methods.com.example.Item=getItemId
                """;

        ByteArrayInputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ChangeTrackingProperties props = ChangeTrackingProperties.loadFromProperties(input);

        assertEquals("uuid", props.getDefaultIdentifier());
        assertEquals(List.of("com.example.vo", "com.example.types"), props.getValueTypePackages());
        assertEquals(List.of("com.example.Money"), props.getValueTypes());
        assertEquals("orderId", props.getIdentifierOverrides().get("com.example.Order"));
        assertEquals("getItemId", props.getIdentifierMethods().get("com.example.Item"));
    }

    @Test
    void shouldUseDefaultWhenPropertyMissing() throws IOException {
        String content = "";
        ByteArrayInputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ChangeTrackingProperties props = ChangeTrackingProperties.loadFromProperties(input);

        assertEquals("id", props.getDefaultIdentifier());
    }
}
