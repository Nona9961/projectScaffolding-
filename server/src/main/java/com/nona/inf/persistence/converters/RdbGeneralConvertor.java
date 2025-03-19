package com.nona.inf.persistence.converters;

import jakarta.annotation.Nullable;

/**
 * 关系型数据库 root和po的转换器
 * <p>
 * 一般来说聚合根的持久化对象是聚合根的子集，所以一般聚合根的持久化对象可以由聚合根的子集来构造，反之则不行，所以需要other辅助。
 *
 * @param <Root>  聚合根
 * @param <PO>    持久化对象
 * @param <Other> 聚合的其他信息
 * @author nona
 */
public interface RdbGeneralConvertor<Root, PO, Other> {

    Class<Root> rootClass();

    Class<PO> poClass();

    PO convertToPO(Root root);

    Root convertToRoot(PO po, @Nullable Other other);
}
