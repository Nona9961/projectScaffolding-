package com.nona.inf.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.TenantId;

/**
 * Tenant-scoped 的 rdb po 基类
 *
 * @author nona
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public abstract class TenantScopedBasePO extends BasePO {

    @Column(nullable = false, length = 64, name = "tenant_id")
    @TenantId
    protected String tenantID;
}
