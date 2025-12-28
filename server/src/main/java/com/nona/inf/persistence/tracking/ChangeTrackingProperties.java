package com.nona.inf.persistence.tracking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

@Data
@ConfigurationProperties(prefix = "change-tracking")
public class ChangeTrackingProperties {

    private String defaultIdentifier = "id";
    private List<String> valueTypePackages = new ArrayList<>();
    private List<String> valueTypes = new ArrayList<>();
    private Map<String, String> identifierOverrides = new HashMap<>();
    private Map<String, String> identifierMethods = new HashMap<>();
}
