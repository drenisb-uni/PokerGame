package main.pokergame.engine;

import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Deck;
import main.pokergame.domain.model.PlayerProfile;
import main.pokergame.domain.model.TableSeat;
import main.pokergame.domain.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PokerGameEngine {
    private PlayerRepository playerRepository;

    private ArrayList<GameEventListener> observers = new ArrayList<>();

    private List<TableSeat> tableSeats;
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;

    public  PokerGameEngine(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        this.tableSeats = new ArrayList<>();
    }

    public boolean joinTable(String username, int buyAmount) {
        PlayerProfile user = playerRepository.findProfileByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found!");
        }
        if (user.getTotalBankroll() < buyAmount) {
            return false;
        }
        return true;
    }

    public void startHand(){

    }

    public void endHand(){
    }

    public void addObserver(GameEventListener observer){
        this.observers.add(observer);
    }
    public void removeObserver(GameEventListener observer){
        this.observers.remove(observer);
    }
}
