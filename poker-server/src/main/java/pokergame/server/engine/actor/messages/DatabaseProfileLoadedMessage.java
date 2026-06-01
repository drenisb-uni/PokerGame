package pokergame.server.engine.actor.messages;

import pokergame.domain.dto.PlayerProfileDTO;

// Non-blocking I/O callbacks dropping data into the actor loop
public record DatabaseProfileLoadedMessage(String playerId, PlayerProfileDTO profile, int buyIn) implements ActorMessage {
}
