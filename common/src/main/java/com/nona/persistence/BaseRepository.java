package com.nona.persistence;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 基础仓储接口
 *
 * @param <ID> id类型
 * @param <Root>  聚合根类型
 */
@ScaffoldGenerated
public interface BaseRepository<ID, Root> {
    Root getByID(ID id);

    boolean save(Root domain);

    int delete(Root domain);

    int deleteByID(ID id);
}