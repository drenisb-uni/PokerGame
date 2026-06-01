package pokergame.server.engine.actor.messages;

public record PlayerDisconnectedMessage(String playerId) implements ActorMessage {
}
