package main.pokergame.engine;
import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Player;
import main.pokergame.domain.rules.HandResult;
import java.util.List;

public interface GameEventListener {

    void onGameStateChanged(GameState newState);

    void onCommunityCardsDealt(List<Card> cards);

    void onPlayerTurn(Player activePlayer, int amountToCall);

    void onPlayerAction(Player player, String actionType, int amount);

    void onHandResult(List<Player> winners, HandResult winningHand, int potSize);
}
