package main.pokergame.domain.rules;

import main.pokergame.domain.model.Card;
import java.util.*;
import java.util.stream.Collectors;

public class HandRanker {

    public HandResult evaluate(List<Card> holeCards, Card[] communityCards) {
        List<Card> allCards = new ArrayList<>(holeCards);
        Collections.addAll(allCards, communityCards);

        allCards.sort((c1, c2) -> Integer.compare(c2.getValue(), c1.getValue()));

        HandResult sfResult = checkStraightFlush(allCards);
        if (sfResult != null) return sfResult;

        HandResult quadsResult = checkFourOfAKind(allCards);
        if (quadsResult != null) return quadsResult;

        HandResult fhResult = checkFullHouse(allCards);
        if (fhResult != null) return fhResult;

        HandResult flushResult = checkFlush(allCards);
        if (flushResult != null) return flushResult;

        HandResult straightResult = checkStraight(allCards);
        if (straightResult != null) return straightResult;

        HandResult tripsResult = checkThreeOfAKind(allCards);
        if (tripsResult != null) return tripsResult;

        HandResult twoPairResult = checkTwoPair(allCards);
        if (twoPairResult != null) return twoPairResult;

        HandResult pairResult = checkOnePair(allCards);
        if (pairResult != null) return pairResult;

        List<Integer> top5Cards = allCards.stream()
                .limit(5)
                .map(Card::getValue)
                .collect(Collectors.toList());

        return new HandResult(HandType.HIGH_CARD, top5Cards);
    }

    private HandResult checkStraightFlush(List<Card> allCards) {
        //najdit tjeter
        return null;
    }


    private HandResult checkFourOfAKind(List<Card> cards) {
        Map<Integer, Integer> counts = getRankCounts(cards);
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 4) {
                int quadRank = entry.getKey();
                int kicker = cards.stream()
                        .filter(c -> c.getValue() != quadRank)
                        .findFirst().get().getValue();
                return new HandResult(HandType.FOUR_OF_A_KIND, Arrays.asList(quadRank, kicker));
            }
        }
        return null;
    }

    private HandResult checkFullHouse(List<Card> cards) {
        Map<Integer, Integer> counts = getRankCounts(cards);
        int tripsRank = -1;
        int pairRank = -1;

        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 3) {
                if (entry.getKey() > tripsRank) tripsRank = entry.getKey();
            } else if (entry.getValue() == 2) {
                if (entry.getKey() > pairRank) pairRank = entry.getKey();
            }
        }

        if (tripsRank != -1 && pairRank != -1) {
            return new HandResult(HandType.FULL_HOUSE, Arrays.asList(tripsRank, pairRank));
        }
        return null;
    }

    private HandResult checkFlush(List<Card> cards) {
        Map<String, List<Card>> suitedCards = cards.stream()
                .collect(Collectors.groupingBy(Card::getSuit));

        for (List<Card> suitList : suitedCards.values()) {
            if (suitList.size() >= 5) {
                List<Integer> flushValues = suitList.stream()
                        .map(Card::getValue)
                        .sorted(Comparator.reverseOrder())
                        .limit(5)
                        .collect(Collectors.toList());
                return new HandResult(HandType.FLUSH, flushValues);
            }
        }
        return null;
    }

    private HandResult checkStraight(List<Card> cards) {
        List<Integer> uniqueRanks = cards.stream()
                .map(Card::getValue)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        for (int i = 0; i <= uniqueRanks.size() - 5; i++) {
            int current = uniqueRanks.get(i);
            if (uniqueRanks.get(i + 4) == current - 4) {
                return new HandResult(HandType.STRAIGHT, Collections.singletonList(current));
            }
        }

        return null;
    }

    private HandResult checkThreeOfAKind(List<Card> cards) {
        return checkNOfAKind(cards, 3, HandType.THREE_OF_A_KIND);
    }

    private HandResult checkTwoPair(List<Card> cards) {
        return checkNOfAKind(cards, 3, HandType.TWO_PAIR);
    }

    private HandResult checkOnePair(List<Card> cards) {
        return checkNOfAKind(cards, 2, HandType.ONE_PAIR);
    }

    private HandResult checkNOfAKind(List<Card> cards, int count, HandType type) {
        Map<Integer, Integer> counts = getRankCounts(cards);
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == count) {
                int rank = entry.getKey();
                List<Integer> kickers = cards.stream()
                        .filter(c -> c.getValue() != rank)
                        .map(Card::getValue)
                        .limit(5 - count)
                        .collect(Collectors.toList());

                List<Integer> resultList = new ArrayList<>();
                resultList.add(rank);
                resultList.addAll(kickers);
                return new HandResult(type, resultList);
            }
        }
        return null;
    }

    private Map<Integer, Integer> getRankCounts(List<Card> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Card c : cards) {
            counts.put(c.getValue(), counts.getOrDefault(c.getValue(), 0) + 1);
        }
        return counts;
    }
}