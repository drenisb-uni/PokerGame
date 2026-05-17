package pokergame.domain.dto;

public record HandParticipantDTO(
        String handId,
        String playerId,
        int seatIndex,
        String holeCards,
        int startChips,
        int endChips,
        int netProfit,
        boolean isWinner
) {
    public HandParticipantDTO sanitizeForNetwork(boolean isShowdown) {
        if (isShowdown) {
            return this;
        }

        return new HandParticipantDTO(
                this.handId,
                this.playerId,
                this.seatIndex,
                "HIDDEN",
                this.startChips,
                this.endChips,
                this.netProfit,
                this.isWinner
        );
    }
}