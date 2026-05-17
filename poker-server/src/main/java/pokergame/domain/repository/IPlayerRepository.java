package pokergame.domain.repository;

import pokergame.domain.dto.PlayerProfileDTO;

public interface IPlayerRepository {
    PlayerProfileDTO findProfileById(String id);
    PlayerProfileDTO findProfileByUsername(String username);
    void saveProfile(PlayerProfileDTO profile);
    void updateProfile(PlayerProfileDTO profile);
}