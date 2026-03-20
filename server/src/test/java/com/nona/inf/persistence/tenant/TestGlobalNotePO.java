package com.nona.inf.persistence.tenant;

import com.nona.inf.persistence.po.BasePO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "t_test_global_note")
public class TestGlobalNotePO extends BasePO {

    @Column(nullable = false, length = 128)
    private String content;
}

