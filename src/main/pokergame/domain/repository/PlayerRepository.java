package pokergame.domain.repository;

import pokergame.domain.dto.PlayerProfileDTO;

public interface PlayerRepository {
    PlayerProfileDTO findProfileById(String id);
    PlayerProfileDTO findProfileByUsername(String username);
    void saveProfile(PlayerProfileDTO profile);
}