package pokergame.engine;

import pokergame.domain.model.TableSeat;

public class BettingPot {
    private int potSize = 0;
    private int highestBetThisRound = 0;
    private int smallBlindAmount = 10;
    private int playersToAct = 0;

    public void resetRound() {
        this.highestBetThisRound = 0;
    }

    public void collectBlinds(TableManager tableManager) {
        int sbIndex = (tableManager.getDealerIndex() + 1) % tableManager.size();
        int bbIndex = (tableManager.getDealerIndex() + 2) % tableManager.size();

        TableSeat sbSeat = tableManager.getSeatAt(sbIndex);
        sbSeat.bet(smallBlindAmount);
        sbSeat.setRoundBet(smallBlindAmount);

        TableSeat bbSeat = tableManager.getSeatAt(bbIndex);
        int bigBlindAmount = smallBlindAmount * 2;
        bbSeat.bet(bigBlindAmount);
        bbSeat.setRoundBet(bigBlindAmount);

        potSize += (smallBlindAmount + bigBlindAmount);
        highestBetThisRound = bigBlindAmount;

        tableManager.setCurrentPlayerIndex((bbIndex + 1) % tableManager.size());
        playersToAct = tableManager.getActivePlayerCount();
    }

    public void handleFold(TableSeat actor){
        actor.setFolded(true);
        actor.addChipsOnTable(-actor.getCurrentRoundBet());
    }

    public void handleCall(TableSeat actor) {
        int amountToCall = highestBetThisRound - actor.getCurrentRoundBet();
        actor.bet(amountToCall);
        actor.setRoundBet(highestBetThisRound);
        potSize += amountToCall;
        decrementPlayersToAct();
    }

    public void handleRaise(TableSeat actor, int raiseAmount, int activePlayersCount) {
        int raiseTotalAmount = raiseAmount + actor.getCurrentRoundBet();
        actor.bet(raiseAmount);
        actor.setRoundBet(raiseTotalAmount);
        potSize += raiseAmount;
        highestBetThisRound = raiseTotalAmount;

        // Reset the loop tracker: everyone else must respond to this raise
        playersToAct = activePlayersCount - 1;
    }

    public boolean isRoundComplete(TableManager tableManager) {
        if (playersToAct > 0) return false;

        for (TableSeat seat : tableManager.getSeats()) {
            if (!seat.isFolded() && seat.getCurrentRoundBet() != highestBetThisRound) {
                return false;
            }
        }
        return true;
    }

    public void awardPotToWinners(java.util.List<TableSeat> winners) {
        int splitPot = potSize / winners.size();
        for (TableSeat winner : winners) {
            winner.addChipsOnTable(splitPot);
        }
    }

    public int getPotSize() { return potSize; }
    public int getHighestBet() { return highestBetThisRound; }
    public void setPlayersToAct(int count) { this.playersToAct = count; }
    public void decrementPlayersToAct() { this.playersToAct--; }
    public void clearPot() { this.potSize = 0; }
}