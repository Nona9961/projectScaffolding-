# projectScaffolding-
项目工程的脚手架，免得每次都从零开始搭建

common模块是公共模块，api模块是接口模块，server模块是服务模块。
一般来说common独立在一个project中，api和server在另一个project中

## 使用 JaVer 进行 DDD 领域对象到 RDB 结构映射的设计

### 1. 自定义变化处理 (Custom Change Processing)

* **目标：** 将 JaVer 产生的过于细粒度的变化事件聚合为更高级别的领域事件，例如“值对象被添加”、“值对象被移除”等，从而忽略值对象内部字段的变更。
* **实现方式：** 创建一个 `CustomChangeProcessor` 类，该类接收 JaVer 的原始变化列表 `List<Change>` 作为输入，并输出一个自定义的领域事件列表 `List<DomainChangeEvent>`.
* **核心逻辑：** 遍历原始变化列表，识别特定的变化模式（例如，先出现列表元素的 `remove` 事件，紧接着出现该元素内部的 `change` 事件），并将其聚合成一个更高级别的领域事件。对于其他类型的变化，可以转换为通用的领域事件。

### 2. 领域事件 (Domain Events)

* **目标：** 使用一组自定义的接口或类来表示领域中发生的有意义的变化，这些事件比 JaVer 的 `Change` 更贴近业务语义。
* **示例：** 可以定义 `ValueObjectRemovedEvent`、`ValueObjectAddedEvent`、`AggregatePropertyChangedEvent` 等接口或类。

### 3. 变更集 (Change Set)

* **目标：** 创建一个 `ChangeSet` 类，用于存储需要应用到 RDB 的所有操作。这个类将包含一系列针对不同 PO 对象的创建、更新或删除操作。
* **结构：** `ChangeSet` 内部维护一个操作列表，每个操作可以包含操作类型（create, update, delete）、目标 PO 类型以及需要操作的数据。

### 4. 领域对象到 PO 的映射策略 (Domain to PO Mapping Strategies)

* **目标：** 使用策略模式，为每种需要持久化的聚合根定义一个专门的映射策略，负责将领域对象的变化（以领域事件的形式）转换为对相应 PO 的操作。
* **实现方式：** 创建一个 `DomainToPoMappingStrategy` 接口，并为每个聚合根实现该接口。每个策略类将接收聚合根对象和领域事件列表作为输入，并生成一个 `ChangeSet`。

### 5. ORM 集成 (ORM Integration)

* **目标：** 创建一个组件（例如 `OrmIntegrator`），负责接收 `ChangeSet`，并使用具体的 ORM 框架（例如 Hibernate, JPA）执行相应的数据库操作。

### Java 伪代码：

```java
// 1. 自定义变化处理器
class CustomChangeProcessor {
    public List<DomainChangeEvent> process(List<Change> rawChanges) {
        List<DomainChangeEvent> domainEvents = new ArrayList<>();
        // ... (识别和聚合原始变化的逻辑)
        // 示例：检测值对象移除
        Map<Object, List<Change>> removedValueObjects = new HashMap<>();
        for (Change change : rawChanges) {
            if (change instanceof ElementRemoved && isValueObject(change.getAffectedObject().get())) {
                removedValueObjects.computeIfAbsent(change.getAffectedObject().get(), k -> new ArrayList<>()).add(change);
            }
        }
        for (Change change : rawChanges) {
            if (change instanceof ElementRemoved && isValueObject(change.getAffectedObject().get())) {
                domainEvents.add(new ValueObjectRemovedEvent(change.getAffectedObject().get()));
            } else if (!(change instanceof ElementRemoved) && !(change instanceof ElementAdded)) {
                // 检查 change 是否属于已移除的值对象，如果是则忽略
                boolean belongsToRemoved = false;
                for (Object removedObject : removedValueObjects.keySet()) {
                    if (change.getAffectedObject().get() == removedObject) {
                        belongsToRemoved = true;
                        break;
                    }
                }
                if (!belongsToRemoved) {
                    domainEvents.add(convertToDomainEvent(change));
                }
            } else {
                domainEvents.add(convertToDomainEvent(change));
            }
        }
        return domainEvents;
    }

    private DomainChangeEvent convertToDomainEvent(Change change) {
        // ... (将 JaVer 的 Change 转换为自定义的 DomainChangeEvent)
        return new GenericChangeEvent(change);
    }

    private boolean isValueObject(Object object) {
        return object instanceof Record; // 或者其他判断值对象的方式
    }
}

// 2. 领域事件 (接口或抽象类)
interface DomainChangeEvent {}

class ValueObjectRemovedEvent implements DomainChangeEvent {
    private Object removedValueObject;
    // ... (构造器和 getter)
}

class ValueObjectAddedEvent implements DomainChangeEvent {
    private Object addedValueObject;
    // ... (构造器和 getter)
}

class GenericChangeEvent implements DomainChangeEvent {
    private Change rawChange;
    // ... (构造器和 getter)
}

// 3. 变更集
class ChangeSet {
    private List<POOperation> operations = new ArrayList<>();

    public void addOperation(POOperation operation) {
        operations.add(operation);
    }

    public List<POOperation> getOperations() {
        return operations;
    }
}

interface POOperation {
    String getOperationType(); // "create", "update", "delete"
    String getEntityType(); // PO 类名
    // ... (存储操作所需的数据，例如字段名和值，或者删除条件)
}

// 4. 领域对象到 PO 的映射策略 (接口)
interface DomainToPoMappingStrategy<T> {
    ChangeSet map(T domainObject, List<DomainChangeEvent> domainEvents);
}

// 针对特定聚合根的映射策略 (示例 - Order 聚合根)
class OrderDomainToPoMappingStrategy implements DomainToPoMappingStrategy<Order> {
    @Override
    public ChangeSet map(Order order, List<DomainChangeEvent> domainEvents) {
        ChangeSet changeSet = new ChangeSet();
        for (DomainChangeEvent event : domainEvents) {
            if (event instanceof ValueObjectRemovedEvent) {
                Object removedItem = ((ValueObjectRemovedEvent) event).getRemovedValueObject();
                if (removedItem instanceof ItemRecord) {
                    changeSet.addOperation(new POOperation() {
                        // ... (创建删除 OrderItemPO 的操作)
                    });
                }
            } else if (event instanceof ValueObjectAddedEvent) {
                Object addedItem = ((ValueObjectAddedEvent) event).getAddedValueObject();
                if (addedItem instanceof ItemRecord) {
                    changeSet.addOperation(new POOperation() {
                        // ... (创建新的 OrderItemPO 的操作)
                    });
                }
            } else if (event instanceof GenericChangeEvent) {
                Change rawChange = ((GenericChangeEvent) event).getRawChange();
                if (rawChange instanceof ObjectChange && !rawChange.getPropertyName().equals("items")) {
                    changeSet.addOperation(new POOperation() {
                        // ... (创建更新 OrderPO 字段的操作)
                    });
                }
            }
        }
        return changeSet;
    }
}

// 5. ORM 集成
class OrmIntegrator {
    public void applyChangeSet(ChangeSet changeSet) {
        for (POOperation operation : changeSet.getOperations()) {
            String operationType = operation.getOperationType();
            String entityType = operation.getEntityType();
            // ... (根据 operationType 和 entityType 执行 ORM 操作)
            if ("create".equals(operationType)) {
                // ...
            } else if ("update".equals(operationType)) {
                // ...
            } else if ("delete".equals(operationType)) {
                // ...
            }
        }
    }
}

// 主流程
class JaVerToRdbMapper {
    private final Javers javers;
    private final CustomChangeProcessor changeProcessor = new CustomChangeProcessor();
    private final Map<Class<?>, DomainToPoMappingStrategy<?>> mappingStrategies = new HashMap<>();
    private final OrmIntegrator ormIntegrator;

    public JaVerToRdbMapper(Javers javers, OrmIntegrator ormIntegrator) {
        this.javers = javers;
        this.ormIntegrator = ormIntegrator;
        mappingStrategies.put(Order.class, new OrderDomainToPoMappingStrategy());
        // ... (注册其他聚合根的映射策略)
    }

    public void mapAndPersist(Object before, Object after) {
        List<Change> rawChanges = javers.compare(before, after).getChanges();
        List<DomainChangeEvent> domainEvents = changeProcessor.process(rawChanges);
        Object aggregateRoot = after; // 假设 'after' 是聚合根
        DomainToPoMappingStrategy strategy = mappingStrategies.get(aggregateRoot.getClass());
        if (strategy != null) {
            ChangeSet changeSet = strategy.map(aggregateRoot, domainEvents);
            ormIntegrator.applyChangeSet(changeSet);
        } else {
            System.err.println("No mapping strategy found for " + aggregateRoot.getClass().getName());
        }
    }

    // ... (main 方法或其他调用入口)
}

// 领域对象和 PO 的伪代码
class Order {
    private Long id;
    private List<ItemRecord> items;
    // ...
}

record ItemRecord(String name, int price) {}

class OrderPO {
    private Long id;
    // ...
}

class OrderItemPO {
    private Long id;
    private Long orderId;
    // ...
}