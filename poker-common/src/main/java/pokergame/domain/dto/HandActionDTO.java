package pokergame.domain.dto;

public record HandActionDTO(
        int id,
        String handId,
        String playerId,
        String roundStage,
        int sequenceNumber,
        String actionType,
        int amount
) {}