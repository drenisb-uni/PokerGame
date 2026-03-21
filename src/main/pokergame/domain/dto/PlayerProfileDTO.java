package pokergame.domain.dto;

import java.time.LocalDateTime;

public record PlayerProfileDTO(
        String id,
        String username,
        int totalBankroll,
        LocalDateTime createdAt
) {}