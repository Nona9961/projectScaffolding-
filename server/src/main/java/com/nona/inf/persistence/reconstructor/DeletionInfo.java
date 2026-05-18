package com.nona.inf.persistence.reconstructor;

import java.util.Objects;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 删除信息（PO 类型 + 主键）
 * <p>
 * id 类型为 Long，与 BasePO.id 保持一致
 * </p>
 *
 * @author nona
 */
@ScaffoldGenerated
public record DeletionInfo(Class<?> poClass, Long id) {

    public DeletionInfo {
        Objects.requireNonNull(poClass, "poClass must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }
}
