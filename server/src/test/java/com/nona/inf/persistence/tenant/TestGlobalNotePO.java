package com.nona.inf.persistence.tenant;

import com.nona.inf.persistence.po.BasePO;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_global_note")
public class TestGlobalNotePO extends BasePO {

    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

