package com.nona.inf.persistence.tenant;

import com.nona.inf.persistence.po.TenantScopedBasePO;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_tenant_note")
public class TestTenantNotePO extends TenantScopedBasePO {

    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

