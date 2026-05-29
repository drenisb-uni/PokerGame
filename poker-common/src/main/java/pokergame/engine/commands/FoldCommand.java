package pokergame.engine.commands;

import pokergame.engine.IPublicActionAPI;

public record FoldCommand(String playerId) implements PlayerCommand {
    @Override public String getPlayerId() { return playerId; }

    @Override
    public void execute(IPublicActionAPI gameEngine) {
        gameEngine.Fold(playerId);
    }
}