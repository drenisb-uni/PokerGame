package pokergame.server.engine.actor.messages;

// Gameplay messages
public record PlayerActionMessage(String playerId, String actionType, int amount) implements ActorMessage {
}
