package pokergame.engine;

import pokergame.domain.model.TableSeat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TableManager {
    private final List<TableSeat> tableSeats = new ArrayList<>();
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;

    public void addSeat(TableSeat seat) { tableSeats.add(seat); }
    public List<TableSeat> getSeats() { return tableSeats; }
    public void clearSeats() { tableSeats.clear(); }
    public int size() { return tableSeats.size(); }

    public TableSeat getCurrentPlayer() { return tableSeats.get(currentPlayerIndex); }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int index) { this.currentPlayerIndex = index; }

    public int getDealerIndex() { return dealerIndex; }
    public void rotateDealer() { dealerIndex = (dealerIndex + 1) % tableSeats.size(); }

    public TableSeat getSeatAt(int index) { return tableSeats.get(index); }

    public int getActivePlayerCount() {
        return (int) tableSeats.stream().filter(seat -> !seat.isFolded()).count();
    }

    public void moveToNextActivePlayer() {
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % tableSeats.size();
        } while (tableSeats.get(currentPlayerIndex).isFolded());
    }

    public Optional<TableSeat> findByUsername(String username) {
        return tableSeats.stream().filter(s -> s.getUsername().equals(username)).findFirst();
    }

    public List<TableSeat> getActivePlayers() {
        return tableSeats.stream().filter(seat -> !seat.isFolded()).toList();
    }
}