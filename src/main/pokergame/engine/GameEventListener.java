package main.pokergame.engine;
import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.TableSeat;
import main.pokergame.domain.rules.HandResult;
import java.util.List;

public interface GameEventListener {

    void onGameStateChanged(GameState newState);

    void onCommunityCardsDealt(List<Card> cards);

    void onPlayerTurn(TableSeat activePlayer, int amountToCall);

    void onPlayerAction(TableSeat player, String actionType, int amount);

    void onHandResult(List<TableSeat> winners, HandResult winningHand, int potSize);
}
