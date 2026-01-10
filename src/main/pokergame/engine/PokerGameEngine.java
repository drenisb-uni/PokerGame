package main.pokergame.engine;

import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Deck;
import main.pokergame.domain.model.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;

public class PokerGameEngine {
    protected ArrayList<Player> playerList;
    protected Deck gameDeck;
    protected Card[] communityCards;
    protected int winningPot;
    protected int smallBlind;
    protected int bigBlind;
    protected Player dealer;
    protected Player playerSmallBlind;
    protected Player playerBigBlind;
    protected int[] playersTotalBets;
    protected boolean keepPlaying;

    public PokerGameEngine(int smallBlind) {
        this.playerList = new ArrayList<Player>();
        this.gameDeck = new Deck();
        this.communityCards = new Card[5];
        this.winningPot = 0;
        this.smallBlind = smallBlind;
        this.bigBlind = this.smallBlind * 2;
        this.keepPlaying = true;

//        gameLoop();
    }

    private void gameLoop() {
        while (this.keepPlaying) {
            boolean isPreFlop = true;
            for (int i = 0; i < 5; i++) {
                bettingRound(isPreFlop);
                isPreFlop = false;
                dealNextCommunityCard();
            }

            rotatePlayerPositions();

            int moneyLeftPerPlayer = 0;
            for (Player player : this.playerList) {
                if (player.getBalance() != 0) {
                    moneyLeftPerPlayer++;
                }
            }
            if (moneyLeftPerPlayer <= 1) {
                this.keepPlaying = false;
            }
            resetCards();
        }
    }

    private void resetCards() {
        this.gameDeck = new Deck();
        this.communityCards = new Card[5];
        for (int i = 0; i < this.playerList.size(); i++) {
            Card[] tempHoleCards = {gameDeck.getNextCard(), gameDeck.getNextCard()};
            this.playerList.get(i).resetHoleCards(tempHoleCards);
        }
    }

    public void playerSetup() {
        this.dealer = playerList.get(0);
        if (playerList.size() > 2) {
            this.playerSmallBlind = playerList.get(1);
            this.playerBigBlind = playerList.get(2);
        }
        this.playersTotalBets = new int[playerList.size()];
        payOutBlinds();
    }

    public void rotatePlayerPositions() {
        int dealerIndex = this.playerList.indexOf(this.dealer);
        int smallBlindIndex = this.playerList.indexOf(this.playerSmallBlind);
        int bigBlindIndex = this.playerList.indexOf(this.playerBigBlind);
        if (dealerIndex < this.playerList.size()-1) {
            this.dealer = this.playerList.get(dealerIndex+1);
        }
        else {
            this.dealer = this.playerList.get(0);
        }
        if (smallBlindIndex < this.playerList.size()-1) {
            this.playerSmallBlind = this.playerList.get(smallBlindIndex+1);
        }
        else {
            this.playerSmallBlind = this.playerList.get(0);
        }
        if (bigBlindIndex < this.playerList.size()-1) {
            this.playerBigBlind = this.playerList.get(bigBlindIndex+1);
        }
        else {
            this.playerBigBlind = this.playerList.get(0);
        }

        for (Player player : this.playerList) player.setIsInGame(true);
    }

    public void payOutBlinds() {
        if (this.playerList.size() > 2) {
            this.playerSmallBlind.reduceToBalance(this.smallBlind);
            this.playersTotalBets[this.playerList.indexOf(playerSmallBlind)] = this.smallBlind;
            this.playerBigBlind.reduceToBalance(this.bigBlind);
            this.playersTotalBets[this.playerList.indexOf(this.playerBigBlind)] = this.bigBlind;
        }
    }

    public void addPlayer(String tempName) {
        int tempBalance = 200;
        Card[] tempHoleCards = {gameDeck.getNextCard(), gameDeck.getNextCard()};
        playerList.add(new Player(tempName, tempBalance, tempHoleCards));
    }

    public void bettingRound(boolean isPreFlop) {
        int startingPlayerIndex;

        if (isPreFlop) {
            startingPlayerIndex = (this.playerList.indexOf(this.playerBigBlind) == this.playerList.size() - 1)
                    ? 0 : this.playerList.indexOf(this.playerBigBlind) + 1;
        }
        else {
            startingPlayerIndex = this.playerList.indexOf(this.playerSmallBlind);
        }

        int currentPlayerIndex = startingPlayerIndex;
        for (int i = 0; i < this.playerList.size(); i++) {
            if (this.playerList.get(currentPlayerIndex).isInGame()) {
                currentPlayerIndex = individualBet(currentPlayerIndex);
            }
        }
        while (!areAllBetsEqual()) {
            currentPlayerIndex = individualBet(currentPlayerIndex);
        }
    }

    public int individualBet(int currentPlayerIndex) {
//        String checkOrCall = areAllBetsEqual() ? "check" : "call";
//        printCommunityCards();
//        if (this.playerList.get(currentPlayerIndex).isInGame()) {
//            System.out.println("The pot is: " + this.winningPot);
//            System.out.println(this.playerList.get(currentPlayerIndex).toString() +
//                    "\nDo you want to fold, " + checkOrCall + " or raise?");
//            if (answer.toLowerCase().contains("fold")) {
//                fold(this.playerList.get(currentPlayerIndex));
//            }
//            else if (answer.toLowerCase().contains("call")) {
//                call(this.playerList.get(currentPlayerIndex));
//            }
//            else if (answer.toLowerCase().contains("raise")) {
//                raise(this.playerList.get(currentPlayerIndex));
//            }
//        }
//        else fold(this.playerList.get(currentPlayerIndex));
//
//        // Check will do nothing in the program just moves the index to the next player
//        currentPlayerIndex++;
//        if (currentPlayerIndex == this.playerList.size()) {
//            currentPlayerIndex = 0;
//        }
        return currentPlayerIndex;
    }

    public boolean areAllBetsEqual() {
        int highestBet = getHighestBet();
        for (int j = 0; j < this.playersTotalBets.length; j++) {
            if (this.playersTotalBets[j] < highestBet && this.playerList.get(j).isInGame()) {
                return false;
            }
        }
        return true;
    }

    public int getHighestBet() {
        int highestBet = 0;
        for (int i = 0; i < this.playersTotalBets.length; i++) {
            if (this.playersTotalBets[i] > highestBet && this.playerList.get(i).isInGame()) {
                highestBet = this.playersTotalBets[i];
            }
        }
        return highestBet;
    }

    public void fold(Player player) {
        if (player.isInGame()) call(player);
        player.setIsInGame(false);
    }

    public void call(Player player) {
        int highestBet = getHighestBet();
        int playersBetDifference = highestBet - (this.playersTotalBets[this.playerList.indexOf(player)]);
        player.reduceToBalance(playersBetDifference);
        this.playersTotalBets[this.playerList.indexOf(player)] += playersBetDifference;
        updateWinningPot();
    }

    public void raise(Player player) {
        System.out.println(player.getName() + ": How much do you want to raise?");
        int raiseAmount = 0;
        call(player);
        player.reduceToBalance(raiseAmount);
        this.playersTotalBets[this.playerList.indexOf(player)] += raiseAmount;
        updateWinningPot();
    }

    public void updateWinningPot() {
        this.winningPot = 0;
        for (int i = 0; i < this.playersTotalBets.length; i++) {
            this.winningPot += this.playersTotalBets[i];
        }
    }

    public void dealNextCommunityCard() {
        if (this.communityCards[0] == null) { // Flop
            this.communityCards[0] = this.gameDeck.getNextCard();
            this.communityCards[1] = this.gameDeck.getNextCard();
            this.communityCards[2] = this.gameDeck.getNextCard();
        }
        else if (this.communityCards[3] == null) { // Turn
            this.communityCards[3] = this.gameDeck.getNextCard();
        }
        else if (this.communityCards[4] == null) {
            this.communityCards[4] = this.gameDeck.getNextCard();
        }
    }

//    public void printCommunityCards() {
//        System.out.println("-----------------------------------------------------");
//        if (this.communityCards[0] != null && this.communityCards[3] == null && this.communityCards[4] == null) { // Flop
//            System.out.println("The flop:");
//        }
//        else if (this.communityCards[3] != null && this.communityCards[4] == null) { // Turn
//            System.out.println("The turn:");
//        }
//        else if (this.communityCards[4] != null) {
//            System.out.println("The river:");
//        }
//        System.out.println(Arrays.toString(this.communityCards));
//        System.out.println("-----------------------------------------------------");
//    }

//    public void findWinner() {
//        ArrayList<Player> winningPlayers = new ArrayList<Player>();
//        //royalFlush
//        winningPlayers = royalFlush();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //straightFlush
//        winningPlayers = straightFlush();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //fourOfAKind
//        winningPlayers = fourOfAKind();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //fullHouse
//        winningPlayers = fullHouse();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //flush
//        winningPlayers = flush();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //straight
//        winningPlayers = straight();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //threeOfAKind
//        winningPlayers = threeOfAKind();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //twoPair
//        winningPlayers = twoPair();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //onePair
//        winningPlayers = onePair();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//        //highCard
//        winningPlayers = highCard();
//        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
//
//        System.out.println("Winner(s) are: " + winningPlayers);
//    }
//
//    public void handleWinners(ArrayList<Player> winningPlayers) {
//        int tempWinnerAward = this.winningPot / winningPlayers.size();
//        for (int i = 0; i < winningPlayers.size(); i++) {
//            winningPlayers.get(i).addToBalance(tempWinnerAward);
//        }
//        this.winningPot = 0;
//    }
//

}
