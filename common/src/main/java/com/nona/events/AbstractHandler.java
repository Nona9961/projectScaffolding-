package com.nona.events;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import com.nona.annotation.ScaffoldGenerated;

/**
 * @author nona
 */
@ScaffoldGenerated
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
        return CompletableFuture.supplyAsync(() -> handle(event), executor);
    }
}
