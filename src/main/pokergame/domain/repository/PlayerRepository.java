package main.pokergame.domain.repository;

import main.pokergame.domain.model.PlayerProfile;

public interface PlayerRepository {
    public PlayerProfile findProfileById(String id);
    public PlayerProfile findProfileByUsername(String username);
    public void saveProfile(PlayerProfile profile);
}
