package com.nona.inf.persistence.po;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import com.nona.annotation.ScaffoldGenerated;


/**
 * 所有rdb的po基类
 *
 * @author nona
 */
@Data
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@ScaffoldGenerated
public abstract class BasePO {

    /**
     * 主键 ID（所有实体统一 Long 类型）
     */
    @Id
    protected Long id;

    /**
     * 创建时间（不可更新；由业务代码手动填充，无自动审计机制——脚手架既有约定）
     */
    @Column(nullable = false, updatable = false)
    protected LocalDateTime createTime;

    /**
     * 更新时间（由业务代码手动维护，无自动审计机制）
     */
    @Column(nullable = false)
    protected LocalDateTime updateTime;


}
