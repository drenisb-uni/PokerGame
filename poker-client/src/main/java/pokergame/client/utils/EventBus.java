package pokergame.client.utils;

import pokergame.domain.dto.GameMessageDTO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EventBus {
    private static final Map<String, List<Consumer<GameMessageDTO>>> listeners = new HashMap<>();

    /**
     * SUBSCRIBER: Controllers call this to listen for specific events.
     */
    public static void subscribe(String eventType, Consumer<GameMessageDTO> action) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(action);
    }

    /**
     * PUBLISHER: The network client calls this to broadcast an event.
     */
    public static void publish(GameMessageDTO event) {
        List<Consumer<GameMessageDTO>> actions = listeners.getOrDefault(event.type(), new ArrayList<>());

        for (Consumer<GameMessageDTO> action : actions) {
            action.accept(event);
        }
    }
}