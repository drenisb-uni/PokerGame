package main.pokergame.domain.rules;
import main.pokergame.domain.model.Card;
import java.util.List;

public interface HandRanker {
    HandResult evaluate(List<Card> holeCards, List<Card> communityCards);
}
