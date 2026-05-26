package pokergame.engine.commands;

import pokergame.engine.IPublicActionAPI;

public record RaiseCommand(String playerId, int amount) implements PlayerCommand {
    @Override public String getPlayerId() { return playerId; }

    @Override
    public void execute(IPublicActionAPI gameEngine) {
        gameEngine.Raise(playerId, amount);
    }
}