package pokergame.engine.commands;

import pokergame.domain.dto.GameMessageDTO;
import pokergame.engine.IPublicActionAPI;
import java.io.Serializable;

public interface PlayerCommand extends Serializable {
    static PlayerCommand fromNetworkMessage(String username, GameMessageDTO message) {
        return switch (message.type()) {
            case "FOLD" -> new FoldCommand(username);
            case "CALL" -> new CallCommand(username);
            case "RAISE" -> new RaiseCommand(username, ((Number) message.payload()).intValue());
            default -> throw new IllegalArgumentException("Unknown command type: " + message.type());
        };
    }

    String getPlayerId();
    void execute(IPublicActionAPI gameEngine);
}