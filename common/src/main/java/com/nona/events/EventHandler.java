package com.nona.events;

import java.util.concurrent.CompletableFuture;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 事件处理接口
 *
 * @param <E> 数据类型
 * @param <R> 返回的结果
 * @author nona
 */
@ScaffoldGenerated
public interface EventHandler<E, R> {
    R handle(Event<E> event);

    CompletableFuture<R> handleAsync(Event<E> event);
}
