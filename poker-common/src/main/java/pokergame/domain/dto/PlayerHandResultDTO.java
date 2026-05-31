package pokergame.domain.dto;

import java.time.LocalDateTime;

public record PlayerHandResultDTO(
        String handId,
        String tableName,
        LocalDateTime playedAt,
        int totalPot,
        String winningHandRank,
        int netProfit,
        boolean winner
) {}
