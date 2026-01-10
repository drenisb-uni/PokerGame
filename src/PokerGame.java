import main.pokergame.domain.model.Player;
import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Deck;

import java.util.*;

public class PokerGame {

    private static PokerGame instance = null;
    

    public static void main(String[] args) {
        PokerGame pg = new PokerGame();
        GameFrame gameFrame = new GameFrame(pg);
    }

    public static synchronized PokerGame getInstance() {
        if (instance == null) {
            instance = new PokerGame();
        }
        return instance;
    }

}