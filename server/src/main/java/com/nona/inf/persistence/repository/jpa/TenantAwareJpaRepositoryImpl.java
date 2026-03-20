package com.nona.inf.persistence.repository.jpa;

import com.nona.inf.context.TenantContext;
import com.nona.inf.persistence.po.TenantScopedBasePO;
import com.nona.util.BusinessAssert;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 多租户隔离（ADR-001 / ADR-007）
 * <p>
 * 对 tenant-scoped 实体自动追加 tenant_id 过滤，并在写操作时自动注入 tenantID。
 * <p>
 * 说明：当前实现仅覆盖 Spring Data JPA 的基础 CRUD 方法（findAll/findById/save/saveAll 等）。
 *
 * @author nona
 */
public class TenantAwareJpaRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;
    private final Class<T> domainClass;
    private final String idAttributeName;
    private final boolean tenantScopedEntity;

    public TenantAwareJpaRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.domainClass = entityInformation.getJavaType();
        this.idAttributeName = entityInformation.getIdAttribute().getName();
        this.tenantScopedEntity = TenantScopedBasePO.class.isAssignableFrom(this.domainClass);
    }

    @Override
    public Optional<T> findById(ID id) {
        if (!tenantScopedEntity || TenantContext.isCrossTenant()) {
            return super.findById(id);
        }
        final String tenantID = normalizeTenantID(TenantContext.getTenantID());
        if (tenantID == null) {
            return Optional.empty();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(domainClass);
        Root<T> root = cq.from(domainClass);

        Predicate byId = cb.equal(root.get(idAttributeName), id);
        Predicate byTenant = cb.equal(root.get(TenantScopedBasePO.TENANT_ID_PARAM), tenantID);
        cq.select(root).where(cb.and(byId, byTenant));

        TypedQuery<T> query = entityManager.createQuery(cq);
        return query.getResultStream().findFirst();
    }

    @Override
    public List<T> findAll() {
        if (!tenantScopedEntity || TenantContext.isCrossTenant()) {
            return super.findAll();
        }
        final String tenantID = normalizeTenantID(TenantContext.getTenantID());
        if (tenantID == null) {
            return List.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(domainClass);
        Root<T> root = cq.from(domainClass);
        cq.select(root).where(cb.equal(root.get(TenantScopedBasePO.TENANT_ID_PARAM), tenantID));
        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    public <S extends T> S save(S entity) {
        ensureAndInjectTenantID(entity);
        return super.save(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        if (entities != null) {
            for (S entity : entities) {
                ensureAndInjectTenantID(entity);
            }
        }
        return super.saveAll(entities);
    }

    private <S extends T> void ensureAndInjectTenantID(S entity) {
        if (!(entity instanceof TenantScopedBasePO po)) {
            return;
        }
        final String tenantID = normalizeTenantID(TenantContext.getTenantID());
        if (TenantContext.isCrossTenant()) {
            final String entityTenantID = normalizeTenantID(po.getTenantID());
            BusinessAssert.assertTrue(!TenantContext.MISSING_TENANT_ID.equals(entityTenantID), "invalid tenantID: {}", entityTenantID);
            if (entityTenantID == null) {
                BusinessAssert.assertNonNull(tenantID, "tenantID is required for tenant-scoped write operation");
                BusinessAssert.assertTrue(!TenantContext.MISSING_TENANT_ID.equals(tenantID), "invalid tenantID: {}", tenantID);
                po.setTenantID(tenantID);
            }
            return;
        }

        BusinessAssert.assertNonNull(tenantID, "tenantID is required for tenant-scoped write operation");
        BusinessAssert.assertTrue(!TenantContext.MISSING_TENANT_ID.equals(tenantID), "invalid tenantID: {}", tenantID);

        final String entityTenantID = normalizeTenantID(po.getTenantID());
        if (entityTenantID == null) {
            po.setTenantID(tenantID);
            return;
        }
        BusinessAssert.assertTrue(tenantID.equals(entityTenantID),
                "cross-tenant write is forbidden. currentTenant={}, entityTenant={}", tenantID, entityTenantID);
    }

    private static String normalizeTenantID(String tenantID) {
        if (tenantID == null || tenantID.isBlank()) {
            return null;
        }
        return tenantID;
    }
}
