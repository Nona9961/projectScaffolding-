package com.nona.inf.persistence.applier;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路径解析工具
 * <p>
 * 解析 ChangeSet 中的路径字符串，如：
 * <ul>
 *     <li>{@code "status"} → 简单字段</li>
 *     <li>{@code "items[123]"} → 索引字段</li>
 *     <li>{@code "items[123].quantity"} → 索引字段 + 嵌套字段</li>
 * </ul>
 */
public class PathParser {

    private static final Pattern INDEXED_PATTERN = Pattern.compile("(\\w+)\\[(.+?)]");

    /**
     * 路径段的密封接口
     */
    public sealed interface PathSegment permits FieldSegment, IndexedSegment {}

    /**
     * 简单字段段
     */
    public record FieldSegment(String fieldName) implements PathSegment {}

    /**
     * 索引字段段（如 items[123]）
     */
    public record IndexedSegment(String fieldName, Object identifier) implements PathSegment {}

    /**
     * 解析后的路径
     */
    public record ParsedPath(List<PathSegment> segments, String fullPath) {}

    /**
     * 解析路径字符串
     *
     * @param path 路径字符串
     * @return 解析后的路径
     */
    public ParsedPath parse(String path) {
        List<PathSegment> segments = new ArrayList<>();
        String[] parts = path.split("\\.");

        for (String part : parts) {
            Matcher matcher = INDEXED_PATTERN.matcher(part);
            if (matcher.matches()) {
                String fieldName = matcher.group(1);
                Object identifier = parseIdentifier(matcher.group(2));
                segments.add(new IndexedSegment(fieldName, identifier));
            } else {
                segments.add(new FieldSegment(part));
            }
        }

        return new ParsedPath(segments, path);
    }

    /**
     * 提取路径的根字段名
     *
     * @param path 路径字符串
     * @return 根字段名
     */
    public String extractRootFieldName(String path) {
        int dotIndex = path.indexOf('.');
        int bracketIndex = path.indexOf('[');

        String firstPart;
        if (dotIndex == -1 && bracketIndex == -1) {
            firstPart = path;
        } else if (dotIndex == -1) {
            firstPart = path.substring(0, bracketIndex);
        } else if (bracketIndex == -1) {
            firstPart = path.substring(0, dotIndex);
        } else {
            firstPart = path.substring(0, Math.min(dotIndex, bracketIndex));
        }

        return firstPart;
    }

    /**
     * 检查路径是否包含索引
     *
     * @param path 路径字符串
     * @return 是否包含索引
     */
    public boolean isIndexedPath(String path) {
        return path.contains("[") && path.contains("]");
    }

    /**
     * 解析标识符（尝试转为 Long，失败则保持字符串）
     */
    private Object parseIdentifier(String identifier) {
        try {
            return Long.parseLong(identifier);
        } catch (NumberFormatException e) {
            return identifier;
        }
    }
}
