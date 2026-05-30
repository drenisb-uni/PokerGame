package pokergame.server.bot;

import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.domain.rules.HandResult;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.engine.commands.CallCommand;
import pokergame.engine.commands.FoldCommand;
import pokergame.engine.commands.RaiseCommand;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.domain.model.TableSeat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BotManager implements IGameEventListener {
    public static final String WAITING_BOT_USERNAME = "Bot_0";

    private final GameCommandProcessor commandProcessor;
    private final PokerGameEngine gameEngine;
    private final Set<String> botUsernames = ConcurrentHashMap.newKeySet();
    private final Map<String, BotPersonality> botPersonalities = new ConcurrentHashMap<>();
    private final Map<String, OpponentProfile> opponentProfiles = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private int botCounter = 1;

    public BotManager(GameCommandProcessor commandProcessor, PokerGameEngine gameEngine) {
        this.commandProcessor = commandProcessor;
        this.gameEngine = gameEngine;
    }

    public void registerBot(String username) {
        botUsernames.add(username);
        botPersonalities.putIfAbsent(username, personalityForName(username));
    }

    public void unregisterBot(String username) {
        botUsernames.remove(username);
        botPersonalities.remove(username);
    }

    public boolean isBot(String username) {
        return botUsernames.contains(username);
    }

    public String nextManualBotName() {
        String username;
        BotPersonality personality = randomPersonality();
        do {
            username = "Bot_" + botCounter++;
        } while (botUsernames.contains(username));
        botPersonalities.put(username, personality);
        return username;
    }

    @Override
    public void onPlayerTurn(String username, int amountToCall) {
        if (!botUsernames.contains(username)) return;

        Thread botThread = new Thread(() -> {
            try {
                Thread.sleep(calculateThinkingDelay(username, amountToCall));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            queueBotDecision(username, amountToCall);
        }, "Bot-Turn-" + username);

        botThread.setDaemon(true);
        botThread.start();
    }

    private void queueBotDecision(String username, int amountToCall) {
        TableSeat botSeat = gameEngine.getPlayerByUsername(username);
        if (botSeat == null || botSeat.isFolded()) {
            return;
        }

        int chips = botSeat.getChipsOnTable();
        if (chips <= 0) {
            commandProcessor.queueCommand(new CallCommand(username));
            return;
        }

        BotPersonality personality = botPersonalities.getOrDefault(username, BotPersonality.BALANCED);
        int strength = clamp(estimateHandStrength(botSeat.getHoleCards(), gameEngine.getCommunityCards()) + personality.strengthBias, 5, 100);
        int pressure = amountToCall == 0 ? 0 : (amountToCall * 100) / Math.max(chips, 1);
        OpponentProfile tableRead = tableRead();
        int adaptedStrength = clamp(strength + adaptiveStrengthBias(tableRead), 5, 100);
        boolean bluffSpot = random.nextDouble() < bluffChance(adaptedStrength, amountToCall, personality, tableRead);
        boolean valueRaise = adaptedStrength >= personality.valueRaiseThreshold && random.nextDouble() < adaptedRaiseChance(personality, tableRead);
        boolean semiBluff = adaptedStrength >= personality.semiBluffThreshold && amountToCall == 0
                && random.nextDouble() < adaptedSemiBluffChance(personality, tableRead);

        if ((valueRaise || semiBluff || bluffSpot) && chips > amountToCall + gameEngine.getBigBlindAmount()) {
            int raiseAmount = chooseRaiseAmount(amountToCall, chips, adaptedStrength, bluffSpot, personality, tableRead);
            commandProcessor.queueCommand(new RaiseCommand(username, raiseAmount));
            return;
        }

        if (amountToCall == 0) {
            commandProcessor.queueCommand(new CallCommand(username));
            return;
        }

        int callThreshold = 18 + (adaptedStrength / 3) + personality.callBonus + adaptiveCallBonus(tableRead);
        if (adaptedStrength >= 78 || pressure <= callThreshold || random.nextDouble() < stubbornCallChance(adaptedStrength, personality, tableRead)) {
            commandProcessor.queueCommand(new CallCommand(username));
        } else {
            commandProcessor.queueCommand(new FoldCommand(username));
        }
    }

    private int calculateThinkingDelay(String username, int amountToCall) {
        TableSeat botSeat = gameEngine.getPlayerByUsername(username);
        int strength = botSeat == null ? 50 : estimateHandStrength(botSeat.getHoleCards(), gameEngine.getCommunityCards());
        int blind = Math.max(gameEngine.getBigBlindAmount(), 1);
        int delay = 550 + random.nextInt(850);

        if (amountToCall > 0) {
            delay += 350;
        }
        if (amountToCall >= blind * 3) {
            delay += 650;
        }
        if (strength >= 38 && strength <= 68) {
            delay += 450;
        }
        if (gameEngine.getCommunityCards().size() >= 4) {
            delay += 250;
        }

        return Math.min(delay, 3200);
    }

    private int chooseRaiseAmount(int amountToCall, int chips, int strength, boolean bluffing, BotPersonality personality,
                                  OpponentProfile tableRead) {
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

    private double bluffChance(int strength, int amountToCall, BotPersonality personality, OpponentProfile tableRead) {
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

    private OpponentProfile tableRead() {
        OpponentProfile combined = new OpponentProfile();
        for (OpponentProfile profile : opponentProfiles.values()) {
            combined.absorb(profile);
        }
        return combined;
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

    private BotPersonality randomPersonality() {
        BotPersonality[] personalities = {
                BotPersonality.BALANCED,
                BotPersonality.TIGHT,
                BotPersonality.LOOSE,
                BotPersonality.AGGRESSIVE,
                BotPersonality.TRICKY
        };
        return personalities[random.nextInt(personalities.length)];
    }

    private BotPersonality personalityForName(String username) {
        if (username.equals(WAITING_BOT_USERNAME)) return BotPersonality.BALANCED;
        if (username.startsWith("Tight_")) return BotPersonality.TIGHT;
        if (username.startsWith("Loose_")) return BotPersonality.LOOSE;
        if (username.startsWith("Aggro_")) return BotPersonality.AGGRESSIVE;
        if (username.startsWith("Tricky_")) return BotPersonality.TRICKY;
        return randomPersonality();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampProbability(double value) {
        return Math.max(0.0, Math.min(0.85, value));
    }

    private enum BotPersonality {
        BALANCED("Balanced", 0, 0, 72, 0.55, 45, 0.22, 1.00, 1.00, 1.00),
        TIGHT("Tight", -7, -8, 78, 0.45, 55, 0.12, 0.55, 0.80, 0.65),
        LOOSE("Loose", 4, 13, 70, 0.48, 42, 0.20, 1.10, 0.90, 1.55),
        AGGRESSIVE("Aggro", 2, 3, 65, 0.72, 38, 0.34, 1.35, 1.30, 1.10),
        TRICKY("Tricky", 0, 5, 70, 0.58, 35, 0.40, 1.80, 1.10, 1.25);

        private final String prefix;
        private final int strengthBias;
        private final int callBonus;
        private final int valueRaiseThreshold;
        private final double valueRaiseChance;
        private final int semiBluffThreshold;
        private final double semiBluffChance;
        private final double bluffScale;
        private final double raiseScale;
        private final double stubbornScale;

        BotPersonality(String prefix, int strengthBias, int callBonus, int valueRaiseThreshold,
                       double valueRaiseChance, int semiBluffThreshold, double semiBluffChance,
                       double bluffScale, double raiseScale, double stubbornScale) {
            this.prefix = prefix;
            this.strengthBias = strengthBias;
            this.callBonus = callBonus;
            this.valueRaiseThreshold = valueRaiseThreshold;
            this.valueRaiseChance = valueRaiseChance;
            this.semiBluffThreshold = semiBluffThreshold;
            this.semiBluffChance = semiBluffChance;
            this.bluffScale = bluffScale;
            this.raiseScale = raiseScale;
            this.stubbornScale = stubbornScale;
        }
    }

    @Override public void onGameStateChanged(GameState state) {}
    @Override public void onCommunityCardsDealt(List<Card> cards) {}
    @Override public void onNewSeatOccupied(HandParticipantDTO participant) {}
    @Override
    public void onPlayerAction(HandActionDTO action) {
        if (botUsernames.contains(action.playerId())) {
            return;
        }

        opponentProfiles
                .computeIfAbsent(action.playerId(), ignored -> new OpponentProfile())
                .record(action.actionType());
    }
    @Override public void onHandResult(List<String> winnerUsernames, HandResult winnerHand, int potSize) {}

    private static class OpponentProfile {
        private int folds;
        private int calls;
        private int raises;
        private int actions;

        void record(String actionType) {
            actions++;
            if ("FOLD".equalsIgnoreCase(actionType) || "LEFT TABLE".equalsIgnoreCase(actionType)) {
                folds++;
            } else if ("RAISE".equalsIgnoreCase(actionType)) {
                raises++;
            } else if ("CALL".equalsIgnoreCase(actionType)) {
                calls++;
            }
        }

        void absorb(OpponentProfile other) {
            this.folds += other.folds;
            this.calls += other.calls;
            this.raises += other.raises;
            this.actions += other.actions;
        }

        double foldRate() {
            return actions == 0 ? 0.33 : (double) folds / actions;
        }

        double callRate() {
            return actions == 0 ? 0.34 : (double) calls / actions;
        }

        double raiseRate() {
            return actions == 0 ? 0.20 : (double) raises / actions;
        }
    }
}
