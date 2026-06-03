package pokergame.domain.dto;

public record HandParticipantDTO(
        String handId,
        String playerUsername,
        int seatIndex,
        String holeCards,
        int startChips,
        int endChips,
        int netProfit,
        boolean hasFolded, // ADDED: Crucial for UI updates
        boolean isAllIn,   // ADDED: Crucial for UI updates
        boolean isWinner
) {
    /**
     * Compact Constructor: Runs automatically on every instantiation.
     * Guarantees we never send null strings over WebSockets, preventing UI crashes.
     */
    public HandParticipantDTO {
        if (holeCards == null || holeCards.isBlank()) {
            holeCards = "HIDDEN";
        }
    }

    /**
     * Convenience Constructor: Tailored specifically for the Client's GameController
     * mapping when receiving a Delta-Update.
     */
    public HandParticipantDTO(String username, int startChips, int endChips, String holeCards, boolean hasFolded, boolean isAllIn) {
        this(
                null,       // handId (Client doesn't need to care)
                username,
                -1,         // seatIndex
                holeCards,
                startChips,
                endChips,
                0,          // netProfit
                hasFolded,
                isAllIn,
                false       // isWinner
        );
    }

    /**
     * Secures the DTO for broadcast to opponents.
     * Optimized to avoid allocating memory if the cards are already hidden.
     */
    public HandParticipantDTO sanitizeForNetwork(boolean isShowdown) {
        // Avoid creating a new object if it's already safe to send
        if (isShowdown || "HIDDEN".equals(this.holeCards)) {
            return this;
        }

        // Return a secure copy
        return new HandParticipantDTO(
                this.handId,
                this.playerUsername,
                this.seatIndex,
                "HIDDEN", // Redacted
                this.startChips,
                this.endChips,
                this.netProfit,
                this.hasFolded,
                this.isAllIn,
                this.isWinner
        );
    }
}