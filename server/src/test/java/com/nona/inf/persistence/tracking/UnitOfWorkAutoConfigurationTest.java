package com.nona.inf.persistence.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.nona.annotation.ScaffoldGenerated;

/**
 * UnitOfWork自动配置测试
 */
@ScaffoldGenerated
class UnitOfWorkAutoConfigurationTest {

    /**
     * 场景1：用户未定义Bean时自动创建
     */
    @Test
    void shouldCreateProviderWhenNoUserDefined() {
        final ChangeTrackingProperties props = new ChangeTrackingProperties();
        props.setDefaultIdentifier("id");
        props.getValueTypePackages().add("com.example.vo");

        final UnitOfWorkAutoConfiguration config = new UnitOfWorkAutoConfiguration();
        final UnitOfWorkProvider provider = config.unitOfWorkProvider(props);

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

        final UnitOfWorkAutoConfiguration config = new UnitOfWorkAutoConfiguration();
        final UnitOfWorkProvider provider = config.unitOfWorkProvider(props);

        assertTrue(provider.getValuePackages().contains("com.example.vo"));
    }
}
