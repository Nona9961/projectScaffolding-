package com.nona.inf.persistence.applier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathParser 单元测试
 */
class PathParserTest {

    private PathParser parser;

    @BeforeEach
    void setUp() {
        parser = new PathParser();
    }

    // ========== 简单字段路径 ==========

    @Test
    void shouldParseSimpleFieldPath() {
        PathParser.ParsedPath result = parser.parse("status");

        assertEquals(1, result.segments().size());
        assertInstanceOf(PathParser.FieldSegment.class, result.segments().get(0));
        assertEquals("status", ((PathParser.FieldSegment) result.segments().get(0)).fieldName());
    }

    @Test
    void shouldParseNestedFieldPath() {
        PathParser.ParsedPath result = parser.parse("address.street");

        assertEquals(2, result.segments().size());
        assertEquals("address", ((PathParser.FieldSegment) result.segments().get(0)).fieldName());
        assertEquals("street", ((PathParser.FieldSegment) result.segments().get(1)).fieldName());
    }

    // ========== 索引路径 ==========

    @Test
    void shouldParseIndexedPathWithNumericId() {
        PathParser.ParsedPath result = parser.parse("items[123]");

        assertEquals(1, result.segments().size());
        assertInstanceOf(PathParser.IndexedSegment.class, result.segments().get(0));
        PathParser.IndexedSegment segment = (PathParser.IndexedSegment) result.segments().get(0);
        assertEquals("items", segment.fieldName());
        assertEquals(123L, segment.identifier());
    }

    @Test
    void shouldParseIndexedPathWithStringId() {
        PathParser.ParsedPath result = parser.parse("items[abc-123]");

        assertEquals(1, result.segments().size());
        PathParser.IndexedSegment segment = (PathParser.IndexedSegment) result.segments().get(0);
        assertEquals("items", segment.fieldName());
        assertEquals("abc-123", segment.identifier());
    }

    // ========== 复合路径 ==========

    @Test
    void shouldParseIndexedPathWithNestedField() {
        PathParser.ParsedPath result = parser.parse("items[123].quantity");

        assertEquals(2, result.segments().size());
        PathParser.IndexedSegment indexed = (PathParser.IndexedSegment) result.segments().get(0);
        assertEquals("items", indexed.fieldName());
        assertEquals(123L, indexed.identifier());

        PathParser.FieldSegment field = (PathParser.FieldSegment) result.segments().get(1);
        assertEquals("quantity", field.fieldName());
    }

    @Test
    void shouldParseComplexPath() {
        PathParser.ParsedPath result = parser.parse("order.items[456].details.name");

        assertEquals(4, result.segments().size());
        assertEquals("order", ((PathParser.FieldSegment) result.segments().get(0)).fieldName());
        assertEquals("items", ((PathParser.IndexedSegment) result.segments().get(1)).fieldName());
        assertEquals(456L, ((PathParser.IndexedSegment) result.segments().get(1)).identifier());
        assertEquals("details", ((PathParser.FieldSegment) result.segments().get(2)).fieldName());
        assertEquals("name", ((PathParser.FieldSegment) result.segments().get(3)).fieldName());
    }

    // ========== 辅助方法 ==========

    @Test
    void shouldExtractRootFieldName() {
        assertEquals("items", parser.extractRootFieldName("items[123]"));
        assertEquals("items", parser.extractRootFieldName("items[123].quantity"));
        assertEquals("status", parser.extractRootFieldName("status"));
        assertEquals("address", parser.extractRootFieldName("address.street"));
    }

    @Test
    void shouldCheckIfPathIsIndexed() {
        assertTrue(parser.isIndexedPath("items[123]"));
        assertTrue(parser.isIndexedPath("items[abc]"));
        assertFalse(parser.isIndexedPath("status"));
        assertFalse(parser.isIndexedPath("address.street"));
    }

    @Test
    void shouldPreserveFullPath() {
        String path = "items[123].quantity";
        PathParser.ParsedPath result = parser.parse(path);
        assertEquals(path, result.fullPath());
    }
}
