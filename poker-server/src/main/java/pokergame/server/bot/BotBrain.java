package pokergame.server.bot;

import pokergame.domain.model.Card;
import pokergame.server.domain.model.TableSeat;
import pokergame.server.engine.PokerGameEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class BotBrain {
    private final Random random = new Random();

    public BotDecision calculateDecision(TableSeat botSeat, int amountToCall, PokerGameEngine gameEngine,
                                         BotPersonality personality, OpponentProfile tableRead) {

        int chips = botSeat.getChipsOnTable();
        if (chips <= 0) {
            return new BotDecision("CALL", 0);
        }

        int strength = clamp(estimateHandStrength(botSeat.getHoleCards(), gameEngine.getCommunityCards()) + personality.strengthBias, 5, 100);
        int pressure = amountToCall == 0 ? 0 : (amountToCall * 100) / Math.max(chips, 1);
        int adaptedStrength = clamp(strength + adaptiveStrengthBias(tableRead), 5, 100);

        boolean bluffSpot = random.nextDouble() < bluffChance(adaptedStrength, amountToCall, personality, tableRead, gameEngine);
        boolean valueRaise = adaptedStrength >= personality.valueRaiseThreshold && random.nextDouble() < adaptedRaiseChance(personality, tableRead);
        boolean semiBluff = adaptedStrength >= personality.semiBluffThreshold && amountToCall == 0
                && random.nextDouble() < adaptedSemiBluffChance(personality, tableRead);

        if ((valueRaise || semiBluff || bluffSpot) && chips > amountToCall + gameEngine.getBigBlindAmount()) {
            int raiseAmount = chooseRaiseAmount(amountToCall, chips, adaptedStrength, bluffSpot, personality, tableRead, gameEngine);
            return new BotDecision("RAISE", raiseAmount);
        }

        if (amountToCall == 0) {
            return new BotDecision("CALL", 0);
        }

        int callThreshold = 18 + (adaptedStrength / 3) + personality.callBonus + adaptiveCallBonus(tableRead);
        if (adaptedStrength >= 78 || pressure <= callThreshold || random.nextDouble() < stubbornCallChance(adaptedStrength, personality, tableRead)) {
            return new BotDecision("CALL", 0);
        } else {
            return new BotDecision("FOLD", 0);
        }
    }

    public int calculateThinkingDelay(TableSeat botSeat, int amountToCall, PokerGameEngine gameEngine) {
        int strength = botSeat == null ? 50 : estimateHandStrength(botSeat.getHoleCards(), gameEngine.getCommunityCards());
        int blind = Math.max(gameEngine.getBigBlindAmount(), 1);
        int delay = 550 + random.nextInt(850);

        if (amountToCall > 0) delay += 350;
        if (amountToCall >= blind * 3) delay += 650;
        if (strength >= 38 && strength <= 68) delay += 450;
        if (gameEngine.getCommunityCards().size() >= 4) delay += 250;

        return Math.min(delay, 3200);
    }

    private int chooseRaiseAmount(int amountToCall, int chips, int strength, boolean bluffing, BotPersonality personality,
                                  OpponentProfile tableRead, PokerGameEngine gameEngine) {
        int blind = Math.max(gameEngine.getBigBlindAmount(), 1);
        int extra;

        if (bluffing) {
            extra = blind * (2 + random.nextInt(3));
        } else if (strength >= 90) {
            extra = blind * (3 + random.nextInt(5));
        } else {
            extra = blind * (2 + random.nextInt(3));
        }

        double adaptiveScale = tableRead.foldRate() > 0.48 ? 1.15 : 1.0;
        if (tableRead.raiseRate() > 0.34 && !bluffing) {
            adaptiveScale += 0.10;
        }
        int total = amountToCall + (int) Math.round(extra * personality.raiseScale * adaptiveScale);
        return Math.max(1, Math.min(total, chips));
    }

    private double bluffChance(int strength, int amountToCall, BotPersonality personality, OpponentProfile tableRead, PokerGameEngine gameEngine) {
        double baseChance;
        if (amountToCall > gameEngine.getBigBlindAmount() * 4) {
            baseChance = 0.03;
        } else if (strength >= 60) {
            baseChance = 0.14;
        } else {
            baseChance = 0.08;
        }
        if (tableRead.foldRate() > 0.52) {
            baseChance += 0.08;
        }
        if (tableRead.callRate() > 0.55) {
            baseChance -= 0.04;
        }
        if (tableRead.raiseRate() > 0.40) {
            baseChance -= 0.03;
        }
        return clampProbability(baseChance * personality.bluffScale);
    }

    private double stubbornCallChance(int strength, BotPersonality personality, OpponentProfile tableRead) {
        double baseChance;
        if (strength >= 60) baseChance = 0.20;
        else if (strength >= 42) baseChance = 0.10;
        else baseChance = 0.03;
        if (tableRead.raiseRate() > 0.35) {
            baseChance += 0.07;
        }
        if (tableRead.foldRate() > 0.55) {
            baseChance -= 0.02;
        }
        return clampProbability(baseChance * personality.stubbornScale);
    }

    private int adaptiveStrengthBias(OpponentProfile tableRead) {
        int bias = 0;
        if (tableRead.raiseRate() > 0.38) bias -= 4;
        if (tableRead.foldRate() > 0.55) bias += 5;
        if (tableRead.callRate() > 0.60) bias += 2;
        return bias;
    }

    private int adaptiveCallBonus(OpponentProfile tableRead) {
        int bonus = 0;
        if (tableRead.raiseRate() > 0.35) bonus += 8;
        if (tableRead.callRate() > 0.58) bonus += 4;
        if (tableRead.foldRate() > 0.58) bonus -= 5;
        return bonus;
    }

    private double adaptedRaiseChance(BotPersonality personality, OpponentProfile tableRead) {
        double chance = personality.valueRaiseChance;
        if (tableRead.foldRate() > 0.52) chance += 0.08;
        if (tableRead.raiseRate() > 0.38) chance -= 0.05;
        return clampProbability(chance);
    }

    private double adaptedSemiBluffChance(BotPersonality personality, OpponentProfile tableRead) {
        double chance = personality.semiBluffChance;
        if (tableRead.foldRate() > 0.50) chance += 0.08;
        if (tableRead.callRate() > 0.60) chance -= 0.04;
        return clampProbability(chance);
    }

    private int estimateHandStrength(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards.size() < 2) {
            return 35;
        }

        List<Card> cards = new ArrayList<>(holeCards);
        cards.addAll(communityCards);

        int score = preflopScore(holeCards);
        score += pairScore(cards);
        score += flushDrawScore(cards);
        score += straightTextureScore(cards);

        if (!communityCards.isEmpty()) {
            score += cards.stream().mapToInt(Card::getValue).max().orElse(0) >= 13 ? 4 : 0;
        }

        return Math.max(5, Math.min(100, score));
    }

    private int preflopScore(List<Card> holeCards) {
        Card first = holeCards.get(0);
        Card second = holeCards.get(1);
        int high = Math.max(first.getValue(), second.getValue());
        int low = Math.min(first.getValue(), second.getValue());
        boolean pair = first.getValue() == second.getValue();
        boolean suited = first.getSuit().equals(second.getSuit());
        int gap = high - low;

        int score = 18 + high * 3 + low;
        if (pair) score += 28 + high;
        if (suited) score += 7;
        if (gap <= 1) score += 6;
        if (gap >= 5) score -= 8;
        if (high >= 14 && low >= 10) score += 10;

        return score;
    }

    private int pairScore(List<Card> cards) {
        return cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getValue, java.util.stream.Collectors.counting()))
                .values()
                .stream()
                .max(Comparator.naturalOrder())
                .map(count -> {
                    if (count >= 4) return 55;
                    if (count == 3) return 38;
                    if (count == 2) return 18;
                    return 0;
                })
                .orElse(0);
    }

    private int flushDrawScore(List<Card> cards) {
        long sameSuitMax = cards.stream()
                .collect(java.util.stream.Collectors.groupingBy(Card::getSuit, java.util.stream.Collectors.counting()))
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);

        if (sameSuitMax >= 5) return 28;
        if (sameSuitMax == 4) return 14;
        return 0;
    }

    private int straightTextureScore(List<Card> cards) {
        List<Integer> values = cards.stream().map(Card::getValue).distinct().sorted().toList();
        int longestRun = 1;
        int currentRun = 1;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) == values.get(i - 1) + 1) {
                currentRun++;
            } else {
                currentRun = 1;
            }
            longestRun = Math.max(longestRun, currentRun);
        }

        if (values.contains(14) && values.contains(2) && values.contains(3) && values.contains(4) && values.contains(5)) {
            longestRun = 5;
        }

        if (longestRun >= 5) return 28;
        if (longestRun == 4) return 13;
        if (longestRun == 3) return 6;
        return 0;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private double clampProbability(double value) {
        return Math.max(0.0, Math.min(0.85, value));
    }
}