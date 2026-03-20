package com.nona.inf.persistence.po;

import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class BasePOInheritanceTest {

    @Test
    void basePoShouldNotDeclareTenantField() {
        assertThrows(NoSuchFieldException.class, () -> BasePO.class.getDeclaredField("tenantID"));
    }

    @Test
    void tenantScopedBasePoShouldDeclareTenantFieldWithTenantIdAnnotation() throws Exception {
        assertTrue(BasePO.class.isAssignableFrom(TenantScopedBasePO.class));

        Field tenantField = TenantScopedBasePO.class.getDeclaredField("tenantID");
        assertNotNull(tenantField.getAnnotation(TenantId.class));
    }
}
