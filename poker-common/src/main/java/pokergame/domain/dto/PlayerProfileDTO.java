package pokergame.domain.dto;

import java.time.LocalDateTime;

public record PlayerProfileDTO(
        String id,
        String username,
        String email,
        String passwordHash,
        int totalBankroll,
        LocalDateTime createdAt
) {}