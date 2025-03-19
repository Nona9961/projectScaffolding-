package com.nona.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于Jackson实现JSON的工具类，封装了Jackson的常用方法
 * <p>
 * 注意所有异常都被丢弃，直接返回空或者空字符串。需要自己保证序列化和反序列化的时候不会出问题。
 * 如果确实需要处理异常，直接使用Jackson的API。
 */
@Slf4j
public class JsonUtil {
    /**
     * 默认的ObjectMapper实例，用于处理JSON序列化和反序列化。想直接使用Jackson的API时，直接使用该MAPPER即可。
     */
    public final static ObjectMapper MAPPER = initMapper();

    public static String toJson(Object obj) {
        return toJson(obj, MAPPER);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return fromJson(json, clazz, MAPPER);
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        return fromJson(json, typeReference, MAPPER);
    }

    /**
     * 将Java对象转换为JSON格式字符串
     *
     * @param obj          需要序列化的Java对象，不能为null
     * @param customMapper 自定义对象映射器，用于控制序列化过程
     *                     允许调用者指定自定义的序列化配置（如日期格式、空值处理等）
     * @return 对象序列化后的JSON字符串，当发生异常时返回空字符串
     * 注意：异常发生时会在日志中记录错误信息，但不会抛出异常
     */
    public static String toJson(Object obj, ObjectMapper customMapper) {
        try {
            return customMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("json序列化出错：{}", e.getMessage());
        }
        return "";
    }

    /**
     * 将JSON字符串反序列化为指定类型的对象
     *
     * @param json         要反序列化的JSON格式字符串
     * @param clazz        目标对象的Class类型（例如MyClass.class）
     * @param customMapper 自定义的ObjectMapper实例，用于控制解析过程
     * @return 解析成功返回对应类型对象，解析失败返回null
     */
    public static <T> T fromJson(String json, Class<T> clazz, ObjectMapper customMapper) {
        try {
            return customMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("json解析出错：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 将JSON字符串反序列化为指定类型的对象，与上一个方法的区别在于该方法可以反序列化带泛型的对象
     * 比如
     * <p>
     * {@code
     * final List<String> strings = fromJson(json, new TypeReference<List<String>>() {});
     * }
     *
     * @param <T>           目标对象泛型类型
     * @param json          需要解析的JSON格式字符串
     * @param typeReference 包含目标类型信息的TypeReference对象（用于处理泛型类型）
     * @param customMapper  自定义的ObjectMapper实例（用于控制反序列化过程）
     * @return 解析成功返回对应类型的对象，解析失败返回null并记录错误日志
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference, ObjectMapper customMapper) {
        try {
            return customMapper.readValue(json, typeReference);
        } catch (Exception e) {
            log.error("json解析出错：{}", e.getMessage());
        }
        return null;
    }

    private static ObjectMapper initMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

}
