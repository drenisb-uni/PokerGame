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
) {}