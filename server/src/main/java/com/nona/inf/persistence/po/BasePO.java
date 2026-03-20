package com.nona.inf.persistence.po;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;


/**
 * 所有rdb的po基类
 *
 * @author nona
 */
@Data
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class BasePO {
    @Id
    protected Long id;
    @Column(nullable = false, updatable = false)
    @CreatedDate
    protected LocalDateTime createTime;
    @Column(nullable = false)
    @LastModifiedDate
    protected LocalDateTime updateTime;


}
