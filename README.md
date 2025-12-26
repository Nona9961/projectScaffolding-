# Project Scaffolding

企业级 Java 后端脚手架，基于 DDD 架构，专注于解决 **DDD 领域模型与关系型数据库之间的阻抗失衡问题**。

## 快速开始

```bash
# 编译并安装
mvn clean install

# 启动（默认 H2 内存数据库）
mvn spring-boot:run -pl server

# 访问
# http://localhost:19890
# H2 控制台：http://localhost:19890/h2
```

## 核心流程

```
┌─────────────────────────────────────────────────────────────┐
│  加载聚合根 → 注册到 UOW → 业务修改 → 计算变更 → 重建 PO → 持久化  │
└─────────────────────────────────────────────────────────────┘
```

## 三步快速上手

### 1. 定义领域模型

```java
// 聚合根
class Order {
    Long id;
    OrderStatus status;
    List<OrderItem> items;  // 子实体

    void addItem(OrderItem item) { items.add(item); }
    void confirm() { status = CONFIRMED; }
}

// 值对象（不会被递归追踪）
record Money(BigDecimal amount) {}
```

### 2. 配置 UOW（核心！）

```java
// 在 Repository 构造时配置
UnitOfWorkProvider uowProvider = UnitOfWorkProvider.builder()
    .withIdentifier(Order.class, Order::getId)
    .withIdentifier(OrderItem.class, OrderItem::getId)
    .withValueType(Money.class)      // 值类型不展开追踪
    .build();

// UOW 自动追踪：
// 1. getByID() 时注册快照
// 2. 修改字段时记录变更
// 3. save() 时生成 ChangeSet
```

### 3. 实现 Repository

```java
// 继承 DifferRepository
class OrderRepositoryImpl extends DifferRepository<Order, OrderPO, Void>
        implements OrderRepository {

    @Override
    protected void doInsert(Order root) {
        orderPO.save(toPO(root));           // 新增主表
        itemPO.saveAll(toPOs(root.items));  // 新增子表
    }

    @Override
    protected void doUpdate(Order root, ChangeSet changeSet) {
        ReconstructedPos pos = reconstructor.reconstruct(root, changeSet);

        orderPO.saveAll(pos.getToSave(OrderPO.class));           // 主表
        itemPO.saveAll(pos.getToSave(OrderItemPO.class));        // 子表新增+更新
        itemPO.deleteAllById(pos.getToDeleteIds(OrderItemPO.class)); // 子表删除
    }
}
```

## 配置说明

### 配置文件（application.yml）

```yaml
change-tracking:
  default-identifier: id              # 默认标识字段
  value-type-packages:                # 值类型包
    - com.nona.domain.order.vo
  value-types:                        # 值类型类
    - com.nona.domain.common.Money
```

### 标识符配置优先级

```
方法配置 > 字段覆盖 > 默认字段
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 虚拟线程 |
| Spring Boot | 3.5.8 | 应用框架 |
| Spring Data JPA | 3.5.8 | ORM |
| Log4j2 | 2.24.3 | 异步日志 |

## 模块结构

```
projectScaffolding/
├── common/     # 公共模块（工具、事件、ID生成）
├── api/        # 接口模块（DTO、API定义）
└── server/     # 主应用（DDD领域模型、持久化）
```

## 项目状态

**已完成**
- [x] DDD 领域模型框架
- [x] 变更追踪与差异持久化
- [x] PoReconstructor 自动重建 PO
- [x] 事件驱动架构
- [x] 分布式 ID 生成

## 许可证

MIT
