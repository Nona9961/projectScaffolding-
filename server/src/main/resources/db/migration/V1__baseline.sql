-- 脚手架模板空基线（V1）
-- 脚手架自身无业务实体，仅承载抽象 PO 基类的表（BasePO / TenantScopedBasePO，TABLE_PER_CLASS 映射）；
-- 派生项目的第一个业务 DDL 从 V2 开始；本文件只增不改（Flyway checksum 保护）。

CREATE TABLE basepo (
    id          BIGINT      NOT NULL PRIMARY KEY,
    create_time TIMESTAMP   NOT NULL,
    update_time TIMESTAMP   NOT NULL
);

CREATE TABLE tenant_scoped_basepo (
    id          BIGINT      NOT NULL PRIMARY KEY,
    create_time TIMESTAMP   NOT NULL,
    update_time TIMESTAMP   NOT NULL,
    tenant_id   VARCHAR(64) NOT NULL
);