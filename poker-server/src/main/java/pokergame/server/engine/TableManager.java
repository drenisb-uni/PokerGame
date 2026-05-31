package pokergame.server.engine;

import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.server.domain.model.TableSeat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TableManager {
    // Defines the max capacity for the table. Adjust this if you play 9-max!
    private static final int MAX_SEATS = 6;

    private final List<TableSeat> tableSeats = new ArrayList<>();
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;

    public TableManager() {
        // 1. THE FIX: Pre-fill the physical table with exactly 6 empty (null) seats.
        // This guarantees the list size NEVER changes, protecting your UI rendering!
        for (int i = 0; i < MAX_SEATS; i++) {
            tableSeats.add(null);
        }
    }

    public List<TableSeat> getSeats() { return tableSeats; }
    public int size() { return MAX_SEATS; }

    public TableSeat getCurrentPlayer() {
        return tableSeats.get(currentPlayerIndex);
    }

    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int index) { this.currentPlayerIndex = index; }
    public int getDealerIndex() { return dealerIndex; }

    public void rotateDealer() {
        if (getActivePlayerCount() == 0) return;

        // 2. FIXED: Skip over empty chairs (nulls) when rotating the button
        do {
            dealerIndex = (dealerIndex + 1) % MAX_SEATS;
        } while (tableSeats.get(dealerIndex) == null);
    }

    public TableSeat getSeatAt(int index) { return tableSeats.get(index); }

    public synchronized int getActivePlayerCount() {
        int activeCount = 0;
        for (TableSeat seat : tableSeats) {
            if (seat != null && !seat.isFolded()) {
                activeCount++;
            }
        }
        return activeCount;
    }

    public void moveToNextActivePlayer() {
        if (getActivePlayerCount() == 0) return;

        int start = currentPlayerIndex;
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % MAX_SEATS;
            if (currentPlayerIndex == start) break;

            // 3. FIXED: Guard against NullPointerExceptions by checking for null FIRST
        } while (tableSeats.get(currentPlayerIndex) == null || tableSeats.get(currentPlayerIndex).isFolded());
    }

    public Optional<TableSeat> findByUsername(String username) {
        // 4. FIXED: Prevent streams from crashing when they encounter an empty seat
        return tableSeats.stream()
                .filter(s -> s != null && s.getUsername().equals(username))
                .findFirst();
    }

    // Inside TableManager.java

    public Optional<TableSeat> findByIdOrUsername(String identifier) {
        return tableSeats.stream()
                .filter(s -> {
                    if (s == null) return false;

                    // 1. Check if it matches the string Username (used by Bots)
                    if (identifier.equals(s.getUsername())) return true;

                    // 2. Check if it matches the UUID Profile ID (used by real Players)
                    if (s.getProfile() != null && identifier.equals(s.getProfile().id())) return true;

                    return false;
                })
                .findFirst();
    }

    public synchronized void removeById(String identifier) {
        for (int i = 0; i < size(); i++) {
            TableSeat seat = tableSeats.get(i);
            if (seat != null && (identifier.equals(seat.getUsername()) ||
                    (seat.getProfile() != null && identifier.equals(seat.getProfile().id())))) {

                System.out.println("[TableManager] Clearing seat position " + i + " for identifier: " + identifier);
                tableSeats.set(i, null); // Wipe the seat
                return;
            }
        }
    }

    public synchronized void removeByUsername(String username) {
        for (int i = 0; i < MAX_SEATS; i++) {
            TableSeat seat = tableSeats.get(i);
            if (seat != null && username.equals(seat.getUsername())) {
                System.out.println("[TableManager] Clearing seat position " + i + " for user: " + username);
                tableSeats.set(i, null);
                return;
            }
        }
    }

    public List<TableSeat> getActivePlayers() {
        return tableSeats.stream()
                .filter(seat -> seat != null && !seat.isFolded())
                .toList();
    }

    public void handleCatastrophicDisconnect(String playerId) {
        findByUsername(playerId).ifPresent(seat -> {
            System.out.println("[TableManager] Handling disconnect for: " + playerId);
            seat.setFolded(true);
            removeByUsername(playerId);

            // If the disconnected player was the current actor, move the turn forward immediately
            if (currentPlayerIndex == seat.getSeatIndex()) {
                moveToNextActivePlayer();
            }
        });
    }

    public boolean sitRealPlayer(PlayerProfileDTO actualProfile, int tableBuyIn) {
        if (findByUsername(actualProfile.id()).isPresent()) {
            System.err.println("[TableManager] Player " + actualProfile.username() + " is already at the table.");
            return false;
        }

        TableSeat newSeat = new TableSeat(actualProfile, tableBuyIn);
        return performSeating(newSeat, actualProfile.username());
    }

    public boolean sitBot(String botName, int tableBuyIn) {
        if (findByUsername(botName).isPresent()) {
            return false;
        }

        PlayerProfileDTO botProfile = new PlayerProfileDTO(
                botName, botName, null, null, tableBuyIn, null
        );

        TableSeat botSeat = new TableSeat(botProfile, tableBuyIn);
        return performSeating(botSeat, botName);
    }

    private boolean performSeating(TableSeat newSeat, String displayName) {
        int openSeatIndex = findFirstOpenSeatIndex();

        if (openSeatIndex == -1) {
            System.err.println("[TableManager] Cannot seat " + displayName + " - Table is full.");
            return false;
        }

        newSeat.setSeatIndex(openSeatIndex);

        // 5. THE FIX: Lock them into the exact array index instead of using .add()
        tableSeats.set(openSeatIndex, newSeat);

        System.out.println("[TableManager] Seated " + displayName + " at index " + openSeatIndex);
        return true;
    }

    private int findFirstOpenSeatIndex() {
        // 6. FIXED: Dramatically simplified. Just find the first null index!
        for (int i = 0; i < MAX_SEATS; i++) {
            if (tableSeats.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    public int getNextActivePlayerIndex(int startIndex) {
        int maxSeats = tableSeats.size(); // Or getSeats().size()

        for (int i = 1; i <= maxSeats; i++) {
            int checkIndex = (startIndex + i) % maxSeats;
            TableSeat seat = getSeatAt(checkIndex); // Or seats.get(checkIndex)

            if (seat != null && seat.getProfile() != null) {
                return checkIndex;
            }
        }
        return startIndex;
    }
}