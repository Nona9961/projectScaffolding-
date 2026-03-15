package com.nona.inf.persistence.tracking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

@Data
@ConfigurationProperties(prefix = "change-tracking")
public class ChangeTrackingProperties {

    /**
     * 指定使用的追踪能力名称（SPI Provider 名称）。
     * <p>
     * 对应 {@code com.nona.changeTracking.spi.TrackingCapabilityProvider#getName()}。
     * <p>
     * 不配置时默认选择策略与 change-tracking 保持一致：
     * <ul>
     *     <li>优先选择 {@code default-reflection}</li>
     *     <li>否则按名称排序选择第一个</li>
     * </ul>
     */
    private String capability;

    private String defaultIdentifier = "id";
    private List<String> valueTypePackages = new ArrayList<>();
    private List<String> valueTypes = new ArrayList<>();
    private Map<String, String> identifierOverrides = new HashMap<>();
    private Map<String, String> identifierMethods = new HashMap<>();
}
