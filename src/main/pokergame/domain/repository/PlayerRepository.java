package pokergame.domain.repository;

import pokergame.domain.model.PlayerProfile;

public interface PlayerRepository {
    public PlayerProfile findProfileById(String id);
    public PlayerProfile findProfileByUsername(String username);
    public void saveProfile(PlayerProfile profile);
}
