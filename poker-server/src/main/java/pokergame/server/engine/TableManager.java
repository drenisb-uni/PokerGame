package pokergame.server.engine;

import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.server.domain.model.TableSeat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TableManager {
    // Defines the max capacity for the table. Adjust this if you play 9-max!
    private static final int MAX_SEATS = 6;

    private final List<TableSeat> tableSeats = new ArrayList<>();
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;

    public void addSeat(TableSeat seat) { tableSeats.add(seat); }
    public List<TableSeat> getSeats() { return tableSeats; }
    public void clearSeats() { tableSeats.clear(); }
    public int size() { return tableSeats.size(); }

    public TableSeat getCurrentPlayer() {
        if (tableSeats.isEmpty()) return null;
        return tableSeats.get(currentPlayerIndex);
    }

    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int index) { this.currentPlayerIndex = index; }

    public int getDealerIndex() { return dealerIndex; }
    public void rotateDealer() {
        if (!tableSeats.isEmpty()) {
            dealerIndex = (dealerIndex + 1) % tableSeats.size();
        }
    }

    public TableSeat getSeatAt(int index) { return tableSeats.get(index); }

    public int getActivePlayerCount() {
        return (int) tableSeats.stream().filter(seat -> !seat.isFolded()).count();
    }

    public void moveToNextActivePlayer() {
        if (tableSeats.isEmpty()) return;

        // Prevent infinite loops if everyone happens to be folded
        int start = currentPlayerIndex;
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % tableSeats.size();
            if (currentPlayerIndex == start) break;
        } while (tableSeats.get(currentPlayerIndex).isFolded());
    }

    public Optional<TableSeat> findByUsername(String username) {
        return tableSeats.stream().filter(s -> s.getUsername().equals(username)).findFirst();
    }

    public boolean removeByUsername(String username) {
        return tableSeats.removeIf(seat -> seat.getUsername().equals(username));
    }

    public List<TableSeat> getActivePlayers() {
        return tableSeats.stream().filter(seat -> !seat.isFolded()).toList();
    }

    /**
     * Safely handles a user's network dropping mid-game.
     * We fold their hand instantly so they don't stall the game loop,
     * and then remove their seat entirely.
     */
    public void handleCatastrophicDisconnect(String playerId) {
        findByUsername(playerId).ifPresent(seat -> {
            System.out.println("[TableManager] Handling disconnect for: " + playerId);
            // 1. Force fold so they don't break the betting round evaluation
            seat.setFolded(true);

            // 2. Remove them from the seat array
            removeByUsername(playerId);

            // 3. Reset indexes if we removed the person whose turn it was
            if (currentPlayerIndex >= tableSeats.size() && !tableSeats.isEmpty()) {
                currentPlayerIndex = 0;
            }
            if (dealerIndex >= tableSeats.size() && !tableSeats.isEmpty()) {
                dealerIndex = 0;
            }
        });
    }

    /**
     * Assigns a player (or bot) to the first available open seat.
     * Generates a basic profile for them. (For authenticated players,
     * this logic can be expanded to accept their real PlayerProfileDTO).
     */
    public void sitPlayerDown(String username, int tableBuyIn) {
        // Prevent duplicate seating
        if (findByUsername(username).isPresent()) {
            return;
        }

        int openSeatIndex = findFirstOpenSeatIndex();

        // No seats available
        if (openSeatIndex == -1) {
            System.err.println("[TableManager] Cannot seat " + username + " - Table is full.");
            return;
        }

        // Create a standard dummy profile for unauthenticated bots or users
        PlayerProfileDTO profile = new PlayerProfileDTO(username, username, null, null, tableBuyIn, null);

        TableSeat newSeat = new TableSeat(profile, tableBuyIn);
        newSeat.setSeatIndex(openSeatIndex);
        addSeat(newSeat);

        System.out.println("[TableManager] Seated " + username + " at index " + openSeatIndex);
    }

    /**
     * Helper Method: Finds the lowest available seat index (0 to MAX_SEATS - 1).
     */
    private int findFirstOpenSeatIndex() {
        Set<Integer> occupiedIndexes = new HashSet<>();
        for (TableSeat seat : tableSeats) {
            occupiedIndexes.add(seat.getSeatIndex());
        }

        for (int i = 0; i < MAX_SEATS; i++) {
            if (!occupiedIndexes.contains(i)) {
                return i;
            }
        }
        return -1; // -1 represents a full table
    }
}