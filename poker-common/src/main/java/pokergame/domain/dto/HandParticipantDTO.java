package pokergame.domain.dto;

public record HandParticipantDTO(
        String handId,
        String playerUsername,
        int seatIndex,
        String holeCards,
        int startChips,
        int endChips,
        int netProfit,
        String cardsToken,
        boolean isWinner
) {
    public HandParticipantDTO(String username, int chips, String token) {
        this(
                null,       // handId
                username,   // playerUsername
                -1,         // seatIndex (placeholder flag)
                null,       // holeCards
                chips,      // startChips -> Read by PlayerSeatController.setup()
                chips,      // endChips
                0,          // netProfit
                token,      // cardToken
                false       // isWinner
        );
    }

    public HandParticipantDTO sanitizeForNetwork(boolean isShowdown) {
        if (isShowdown) {
            return this;
        }

        return new HandParticipantDTO(
                this.handId,
                this.playerUsername,
                this.seatIndex,
                "HIDDEN",
                this.startChips,
                this.endChips,
                this.netProfit,
                this.cardsToken,
                this.isWinner
        );
    }
}