package pokergame.engine.commands;

import pokergame.engine.IPublicActionAPI;
import java.io.Serializable;

public interface PlayerCommand extends Serializable {
    String getPlayerId();
    void execute(IPublicActionAPI gameEngine);
}