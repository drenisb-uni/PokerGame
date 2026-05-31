package pokergame.engine.commands;

import pokergame.engine.IPublicActionAPI;

public record JoinTableCommand(String playerID, int amount) implements PlayerCommand {
    @Override
    public String getPlayerId() {
        return playerID;
    }

    @Override
    public void execute(IPublicActionAPI gameEngine) {
        gameEngine.JoinTable(playerID, amount);
    }
}
