package com.nona.events;

import java.util.concurrent.CompletableFuture;

/**
 * 分发器
 *
 * @author nona
 */
public interface Dispatcher {
    <E, R> void register(String eventType, EventHandler<E, R> handler);

    <E, R> R dispatch(Event<E> event);

    <E, R> CompletableFuture<R> dispatchAsync(Event<E> event);
}
