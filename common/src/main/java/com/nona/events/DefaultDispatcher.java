package com.nona.events;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 分发器的默认实现
 *
 * @author nona
 */
@ScaffoldGenerated
public class DefaultDispatcher implements Dispatcher {

    private final Map<String, EventHandler<?, ?>> handlers = new LinkedHashMap<>();

    @Override
    public <E, R> void register(String eventType, EventHandler<E, R> handler) {
        if (StringUtils.isBlank(eventType)) {
            throw new IllegalArgumentException("cannot register a handler with a blank type");
        }
        Objects.requireNonNull(handler);
        handlers.put(eventType, handler);
    }

    @Override
    public <E, R> R dispatch(Event<E> event) {
        final EventHandler<E, R> handler = findHandler(event);
        return handler.handle(event);
    }

    @Override
    public <E, R> CompletableFuture<R> dispatchAsync(Event<E> event) {
        final EventHandler<E, R> handler = findHandler(event);
        return handler.handleAsync(event);
    }

    @SuppressWarnings("unchecked")
    private <E, R> EventHandler<E, R> findHandler(Event<E> event) {
        final EventHandler<?, ?> eventHandler = handlers.get(event.getType());
        Objects.requireNonNull(eventHandler, "no such handler for event type " + event.getType());
        return (EventHandler<E, R>) eventHandler;
    }
}
