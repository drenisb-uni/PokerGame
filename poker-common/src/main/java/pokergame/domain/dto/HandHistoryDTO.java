package pokergame.domain.dto;

import java.time.LocalDateTime;

public record HandHistoryDTO(
        String id,
        int tableId,
        LocalDateTime startedAt,
        String communityCards,
        int totalPot,
        String winningHandRank
) {}