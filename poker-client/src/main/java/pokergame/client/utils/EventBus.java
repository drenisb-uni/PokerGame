package pokergame.client.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {
    private static final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    /**
     * Subscribes a handler callback to a specific class type payload.
     */
    @SuppressWarnings("unchecked")
    public static <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add((Consumer<Object>) handler);
    }

    /**
     * Unsubscribes a specific handler to prevent memory leaks and duplicate UI updates.
     */
    public static <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<Object>> handlers = listeners.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    /**
     * Publishes an event instance to all registered handlers matching its exact class type.
     */
    public static void publish(Object event) {
        if (event == null) return;
        Class<?> eventType = event.getClass();
        List<Consumer<Object>> handlers = listeners.get(eventType);

        if (handlers != null) {
            // Operate on a copy to prevent ConcurrentModificationException during scene changes
            for (Consumer<Object> handler : new ArrayList<>(handlers)) {
                try {
                    handler.accept(event);
                } catch (Exception e) {
                    System.err.println("[EventBus Error] Subscriber handling failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Optional utility to clear subscribers when changing scenes or tearing down tables
     */
    public static void clear() {
        listeners.clear();
    }
}