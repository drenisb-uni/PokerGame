package pokergame.server.engine;

import pokergame.server.domain.model.TableSeat;

import java.util.List;

public class BettingPot {
    private int potSize = 0;
    private int foldedPot = 0;
    private int highestBetThisRound = 0;
    private int smallBlindAmount = 10;
    private int playersToAct = 0;

    public void setSmallBlindAmount(int smallBlindAmount) {
        this.smallBlindAmount = Math.max(1, smallBlindAmount);
    }

    public void resetRound(TableManager tableManager) {
        this.highestBetThisRound = 0;

        for (TableSeat seat : tableManager.getSeats()) {
            if (seat == null) continue;
            seat.setRoundBet(0);
        }
    }

    public void collectBlinds(TableManager tableManager) {
        int dealerIndex = tableManager.getDealerIndex();

        // 1. Find the Small Blind (First actual player after the dealer)
        int sbIndex = tableManager.getNextActivePlayerIndex(dealerIndex);
        TableSeat sbSeat = tableManager.getSeatAt(sbIndex);

        // 2. Find the Big Blind (First actual player after the Small Blind)
        int bbIndex = tableManager.getNextActivePlayerIndex(sbIndex);
        TableSeat bbSeat = tableManager.getSeatAt(bbIndex);

        // 3. Find "Under the Gun" (First actual player after the Big Blind to act pre-flop)
        int utgIndex = tableManager.getNextActivePlayerIndex(bbIndex);

        // --- Process Small Blind ---
        sbSeat.bet(smallBlindAmount);
        sbSeat.setRoundBet(smallBlindAmount);

        // --- Process Big Blind ---
        int bigBlindAmount = smallBlindAmount * 2;
        bbSeat.bet(bigBlindAmount);
        bbSeat.setRoundBet(bigBlindAmount);

        // --- Update Pot Math ---
        potSize += (smallBlindAmount + bigBlindAmount);
        highestBetThisRound = bigBlindAmount;

        // --- Safely Set the Next Turn! ---
        tableManager.setCurrentPlayerIndex(utgIndex);
        playersToAct = tableManager.getActivePlayerCount();
    }

    public void handleFold(TableSeat actor){
        actor.setFolded(true);
        decrementPlayersToAct();
    }

    public void handleCall(TableSeat actor) {
        int amountToCall = highestBetThisRound - actor.getCurrentRoundBet();
        actor.bet(amountToCall);
        actor.setRoundBet(highestBetThisRound);
        potSize += amountToCall;
        decrementPlayersToAct();
    }

    public void handleRaise(TableSeat actor, int raiseToTarget, int activePlayersCount) {
        int additionalChipsToDeduct = raiseToTarget - actor.getCurrentRoundBet();

        actor.bet(additionalChipsToDeduct);
        actor.setRoundBet(raiseToTarget); // Their new total round contribution

        this.potSize += additionalChipsToDeduct;
        this.highestBetThisRound = raiseToTarget; // The new ceiling everyone must match

        this.playersToAct = activePlayersCount - 1;
    }

    public boolean isRoundComplete(TableManager tableManager) {
        if (playersToAct > 0) return false;

        for (TableSeat seat : tableManager.getSeats()) {
            if (seat == null) continue;
            if (!seat.isFolded() && seat.getCurrentRoundBet() != highestBetThisRound) {
                return false;
            }
        }
        return true;
    }

    public void awardPotToWinners(List<TableSeat> winners) {
        int splitPot = potSize / winners.size();
        int oddChips = potSize % winners.size(); // Get the leftover chips!

        for (int i = 0; i < winners.size(); i++) {
            int amountToAward = splitPot;
            if (i == 0) amountToAward += oddChips; // Give the extra chips to the first winner

            winners.get(i).addChipsOnTable(amountToAward);
        }
    }

    public int getPotSize() { return potSize; }
    public int getFoldedPot() { return foldedPot; }
    public int getHighestBet() { return highestBetThisRound; }
    public int getSmallBlindAmount() { return smallBlindAmount; }
    public int getBigBlindAmount() { return smallBlindAmount * 2; }
    public void setPlayersToAct(int count) { this.playersToAct = count; }
    public void decrementPlayersToAct() { this.playersToAct--; }
    public void clearPot() { this.potSize = 0; }

    public void resetAll() {
        this.potSize = 0;
        this.foldedPot = 0;
        this.highestBetThisRound = 0;
        this.playersToAct = 0;
    }
}
