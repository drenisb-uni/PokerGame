import main.pokergame.domain.model.Player;
import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Deck;

import java.util.*;

public class PokerGame {

    private static PokerGame instance = null;
    
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
    Scanner scan = new Scanner(System.in);

    public PokerGame(int smallBlind) {
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

            findWinner();
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
        String checkOrCall = areAllBetsEqual() ? "check" : "call";
        printCommunityCards();
        if (this.playerList.get(currentPlayerIndex).isInGame()) {
            System.out.println("The pot is: " + this.winningPot);
            System.out.println(this.playerList.get(currentPlayerIndex).toString() +
                    "\nDo you want to fold, " + checkOrCall + " or raise?");
            String answer = scan.next();
            if (answer.toLowerCase().contains("fold")) {
                fold(this.playerList.get(currentPlayerIndex));
            }
            else if (answer.toLowerCase().contains("call")) {
                call(this.playerList.get(currentPlayerIndex));
            }
            else if (answer.toLowerCase().contains("raise")) {
                raise(this.playerList.get(currentPlayerIndex));
            }
        }
        else fold(this.playerList.get(currentPlayerIndex));

        // Check will do nothing in the program just moves the index to the next player
        currentPlayerIndex++;
        if (currentPlayerIndex == this.playerList.size()) {
            currentPlayerIndex = 0;
        }
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
        int raiseAmount = scan.nextInt();
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

    public void printCommunityCards() {
        System.out.println("-----------------------------------------------------");
        if (this.communityCards[0] != null && this.communityCards[3] == null && this.communityCards[4] == null) { // Flop
            System.out.println("The flop:");
        }
        else if (this.communityCards[3] != null && this.communityCards[4] == null) { // Turn
            System.out.println("The turn:");
        }
        else if (this.communityCards[4] != null) {
            System.out.println("The river:");
        }
        System.out.println(Arrays.toString(this.communityCards));
        System.out.println("-----------------------------------------------------");
    }

    public void findWinner() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        //royalFlush
        winningPlayers = royalFlush();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //straightFlush
        winningPlayers = straightFlush();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //fourOfAKind
        winningPlayers = fourOfAKind();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //fullHouse
        winningPlayers = fullHouse();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //flush
        winningPlayers = flush();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //straight
        winningPlayers = straight();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //threeOfAKind
        winningPlayers = threeOfAKind();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //twoPair
        winningPlayers = twoPair();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //onePair
        winningPlayers = onePair();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);
        //highCard
        winningPlayers = highCard();
        if (!winningPlayers.isEmpty()) handleWinners(winningPlayers);

        System.out.println("Winner(s) are: " + winningPlayers);
    }

    public void handleWinners(ArrayList<Player> winningPlayers) {
        int tempWinnerAward = this.winningPot / winningPlayers.size();
        for (int i = 0; i < winningPlayers.size(); i++) {
            winningPlayers.get(i).addToBalance(tempWinnerAward);
        }
        this.winningPot = 0;
    }

    public ArrayList<Player> royalFlush() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            int hearts = 0;
            int diamonds = 0;
            int spades = 0;
            int clubs = 0;
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                ArrayList<Integer> tempPlayerCards = new ArrayList<Integer>();
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int l = 0; l < tempPlayersCardsAll.size(); l++) {
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Hearts")) {
                        hearts++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Diamonds")) {
                        diamonds++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Spades")) {
                        spades++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Clubs")) {
                        clubs++;
                    }
                }
                if (hearts >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Hearts");
                }
                else if (diamonds >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Diamonds");
                }
                else if (spades >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Spades");
                }
                else if (clubs >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Clubs");
                }

                if (tempPlayerCards.contains(10) &&
                        tempPlayerCards.contains(11) &&
                        tempPlayerCards.contains(12) &&
                        tempPlayerCards.contains(13) &&
                        tempPlayerCards.contains(14)) {
                    winningPlayers.add(tempPlayer);
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> straightFlush() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            int hearts = 0;
            int diamonds = 0;
            int spades = 0;
            int clubs = 0;
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                ArrayList<Integer> tempPlayerCards = new ArrayList<Integer>();
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int l = 0; l < tempPlayersCardsAll.size(); l++) {
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Hearts")) {
                        hearts++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Diamonds")) {
                        diamonds++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Spades")) {
                        spades++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Clubs")) {
                        clubs++;
                    }
                }
                if (hearts >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Hearts");
                }
                else if (diamonds >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Diamonds");
                }
                else if (spades >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Spades");
                }
                else if (clubs >= 5) {
                    tempPlayerCards = getValuesOfSuit(tempPlayersCardsAll, "Clubs");
                }
                Collections.sort(tempPlayerCards);
                int lowestValue = 15;
                int secondHighestValue = 15;
                int thirdHighestValue = 15;
                if (tempPlayerCards.size() >= 5) {
                    lowestValue = tempPlayerCards.get(0);
                    secondHighestValue = tempPlayerCards.get(1);
                    thirdHighestValue = tempPlayerCards.get(2);
                    if(tempPlayerCards.contains(lowestValue + 1) &&
                            tempPlayerCards.contains(lowestValue + 2) &&
                            tempPlayerCards.contains(lowestValue + 3) &&
                            tempPlayerCards.contains(lowestValue + 4)) {
                        winningPlayers.add(tempPlayer);
                    }
                }
                if (tempPlayerCards.size() >= 6) {
                    if(tempPlayerCards.contains(secondHighestValue + 1) &&
                            tempPlayerCards.contains(secondHighestValue + 2) &&
                            tempPlayerCards.contains(secondHighestValue + 3) &&
                            tempPlayerCards.contains(secondHighestValue + 4)) {
                        winningPlayers.add(tempPlayer);
                    }
                }
                if (tempPlayerCards.size() >= 7) {
                    if(tempPlayerCards.contains(thirdHighestValue + 1) &&
                            tempPlayerCards.contains(thirdHighestValue + 2) &&
                            tempPlayerCards.contains(thirdHighestValue + 3) &&
                            tempPlayerCards.contains(thirdHighestValue + 4)) {
                        winningPlayers.add(tempPlayer);
                    }
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> fourOfAKind() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                int [] allValues = new int[13];
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int j = 0; j < tempPlayersCardsAll.size(); j++) {
                    allValues[tempPlayersCardsAll.get(j).getValue()-2]++;
                }
                for (int k = 0; k < allValues.length; k++) {
                    if (allValues[k] >= 4) {
                        winningPlayers.add(tempPlayer);
                    }
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> fullHouse() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                int [] allValues = new int[13];
                boolean threeCard = false;
                boolean twoCard = false;

                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int j = 0; j < tempPlayersCardsAll.size(); j++) {
                    allValues[tempPlayersCardsAll.get(j).getValue()-2]++;
                }
                for (int k = 0; k < allValues.length; k++) {
                    if (allValues[k] >= 3) {
                        threeCard = true;
                    }
                    else if (allValues[k] >= 2) {
                        twoCard = true;
                    }
                }
                if (threeCard && twoCard) {
                    winningPlayers.add(tempPlayer);
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> flush() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            int hearts = 0;
            int diamonds = 0;
            int spades = 0;
            int clubs = 0;
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int l = 0; l < tempPlayersCardsAll.size(); l++) {
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Hearts")) {
                        hearts++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Diamonds")) {
                        diamonds++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Spades")) {
                        spades++;
                    }
                    if (tempPlayersCardsAll.get(l).getSuit().contains("Clubs")) {
                        clubs++;
                    }
                }
                if (hearts >= 5 || diamonds >= 5 || spades >= 5 || clubs >= 5) {
                    winningPlayers.add(tempPlayer);
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> straight() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                ArrayList<Integer> tempPlayerCards = getValuesOfAllCards(tempPlayersCardsAll);

                Collections.sort(tempPlayerCards);
                int lowestValue = 15;
                int secondHighestValue = 15;
                int thirdHighestValue = 15;
                if (tempPlayerCards.size() >= 5) {
                    lowestValue = tempPlayerCards.get(0);
                    secondHighestValue = tempPlayerCards.get(1);
                    thirdHighestValue = tempPlayerCards.get(2);
                    if(tempPlayerCards.contains(lowestValue + 1) &&
                            tempPlayerCards.contains(lowestValue + 2) &&
                            tempPlayerCards.contains(lowestValue + 3) &&
                            tempPlayerCards.contains(lowestValue + 4)) {
                        winningPlayers.add(tempPlayer);
                    }
                }
                if (tempPlayerCards.size() >= 6) {
                    if(tempPlayerCards.contains(secondHighestValue + 1) &&
                            tempPlayerCards.contains(secondHighestValue + 2) &&
                            tempPlayerCards.contains(secondHighestValue + 3) &&
                            tempPlayerCards.contains(secondHighestValue + 4)) {
                        winningPlayers.add(tempPlayer);
                    }
                }
                if (tempPlayerCards.size() >= 7) {
                    if(tempPlayerCards.contains(thirdHighestValue + 1) &&
                            tempPlayerCards.contains(thirdHighestValue + 2) &&
                            tempPlayerCards.contains(thirdHighestValue + 3) &&
                            tempPlayerCards.contains(thirdHighestValue + 4)) {
                        winningPlayers.add(tempPlayer);
                    }
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> threeOfAKind() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                int [] allValues = new int[13];
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int j = 0; j < tempPlayersCardsAll.size(); j++) {
                    allValues[tempPlayersCardsAll.get(j).getValue()-2]++;
                }
                for (int k = 0; k < allValues.length; k++) {
                    if (allValues[k] >= 3) {
                        winningPlayers.add(tempPlayer);
                    }
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> twoPair() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                int [] allValues = new int[13];
                int pairCount = 0;
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int j = 0; j < tempPlayersCardsAll.size(); j++) {
                    allValues[tempPlayersCardsAll.get(j).getValue()-2]++;
                }
                for (int k = 0; k < allValues.length; k++) {
                    if (allValues[k] >= 2) {
                        pairCount++;
                        if (pairCount == 2) {
                            winningPlayers.add(tempPlayer);
                        }
                    }
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> onePair() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        for (int i = 0; i < this.playerList.size(); i++) {
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                int [] allValues = new int[13];
                ArrayList<Card> tempPlayersCardsAll = tempPlayersCardsAll(tempPlayer);
                for (int j = 0; j < tempPlayersCardsAll.size(); j++) {
                    allValues[tempPlayersCardsAll.get(j).getValue()-2]++;
                }
                for (int k = 0; k < allValues.length; k++) {
                    if (allValues[k] >= 2 && !winningPlayers.contains(tempPlayer)) {
                        winningPlayers.add(tempPlayer);
                    }
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Player> highCard() {
        ArrayList<Player> winningPlayers = new ArrayList<Player>();
        int[][] sortedPlayersHoleCards = new int[this.playerList.size()][2];
        // The first highest card
        int highestCardColTwo = 0;
        // The second highest card
        int highestCardColOne = 0;
        boolean tie = false;

        for (int i = 0; i < this.playerList.size(); i++) {
            Player tempPlayer = this.playerList.get(i);
            if (tempPlayer.isInGame()) {
                ArrayList<Integer> tempHoleCards = new ArrayList<Integer>();
                tempHoleCards.add(tempPlayer.getHoleCards()[0].getValue());
                tempHoleCards.add(tempPlayer.getHoleCards()[1].getValue());
                Collections.sort(tempHoleCards);
                if (tempHoleCards.get(1) > highestCardColTwo) {
                    highestCardColTwo = tempHoleCards.get(1);
                    winningPlayers.clear();
                    winningPlayers.add(tempPlayer);
                    sortedPlayersHoleCards = new int[this.playerList.size()][2];
                    sortedPlayersHoleCards[i][0] = i;
                    sortedPlayersHoleCards[i][1] = tempHoleCards.get(0);
                }
                else if (tempHoleCards.get(1) == highestCardColTwo) {
                    winningPlayers.add(tempPlayer);
                    tie = true;
                    // Saves the index of the player and the value of the players second highest card
                    sortedPlayersHoleCards[i][0] = i;
                    sortedPlayersHoleCards[i][1] = tempHoleCards.get(0);
                }
            }
        }
        if (tie) {
            winningPlayers.clear();
            for (int k = 0; k < sortedPlayersHoleCards.length; k++) {
                if (sortedPlayersHoleCards[k][1] > highestCardColOne) {
                    highestCardColOne = sortedPlayersHoleCards[k][1];
                }
            }
            for (int l = 0; l < sortedPlayersHoleCards.length; l++) {
                if (sortedPlayersHoleCards[l][1] == highestCardColOne) {
                    winningPlayers.add(this.playerList.get(l));
                }
            }
        }
        return winningPlayers;
    }

    public ArrayList<Card> tempPlayersCardsAll(Player player) {
        ArrayList<Card> result = new ArrayList<Card>();
        for (int i = 0; i < this.communityCards.length; i++) {
            result.add(this.communityCards[i]);
        }
        result.add(player.getHoleCards()[0]);
        result.add(player.getHoleCards()[1]);
        return result;
    }

    public ArrayList<Integer> getValuesOfSuit(ArrayList<Card> playersCardsAll, String suit) {
        ArrayList<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < playersCardsAll.size(); i++) {
            if (playersCardsAll.get(i).getSuit().contains(suit)) {
                result.add(playersCardsAll.get(i).getValue());
            }
        }
        return result;
    }

    public ArrayList<Integer> getValuesOfAllCards(ArrayList<Card> playersCardsAll) {
        ArrayList<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < playersCardsAll.size(); i++) {
            result.add(playersCardsAll.get(i).getValue());
        }
        return result;
    }

    public int getPlayerCount() {
        return this.playerList.size();
    }

    public static void main(String[] args) {
        PokerGame pg = new PokerGame(1);
        GameFrame gameFrame = new GameFrame(pg);
    }

    public static synchronized PokerGame getInstance() {
        if (instance == null) {
            instance = new PokerGame(1);
        }
        return instance;
    }

}