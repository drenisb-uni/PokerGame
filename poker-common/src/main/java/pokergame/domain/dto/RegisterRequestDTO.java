package pokergame.domain.dto;

public record RegisterRequestDTO(
        String username,
        String email,
        String password
) {
}
