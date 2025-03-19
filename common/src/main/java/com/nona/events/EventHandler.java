package com.nona.events;

import java.util.concurrent.CompletableFuture;

/**
 * 事件处理接口
 *
 * @param <E> 数据类型
 * @param <R> 返回的结果
 * @author nona
 */
public interface EventHandler<E, R> {
    R handle(Event<E> event);

    CompletableFuture<R> handleAsync(Event<E> event);
}
