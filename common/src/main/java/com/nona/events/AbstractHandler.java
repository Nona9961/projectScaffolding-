package com.nona.events;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author nona
 */
public abstract class AbstractHandler<E, R> implements EventHandler<E, R> {

    private static final Executor executor = buildVirtualExecutor();

    private static Executor buildVirtualExecutor() {
        final ThreadFactory factory = Thread.ofVirtual()
                .name("handler-", 0)
                .factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    @Override
    public CompletableFuture<R> handleAsync(Event<E> event) {
        // 使用executor异步执行handle方法
        return CompletableFuture.supplyAsync(() -> handle(event), executor);
    }
}
