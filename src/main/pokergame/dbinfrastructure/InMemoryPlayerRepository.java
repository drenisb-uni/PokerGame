package main.pokergame.dbinfrastructure;

import main.pokergame.domain.model.PlayerProfile;
import main.pokergame.domain.repository.PlayerRepository;

import java.util.HashMap;
import java.util.Map;

public class InMemoryPlayerRepository implements PlayerRepository {

    private Map<String, PlayerProfile> db = new HashMap<>();

    public InMemoryPlayerRepository() {
        saveProfile(new PlayerProfile("1","drenis",1000));
        saveProfile(new PlayerProfile("2","sinerd",1000));
    }

    @Override
    public PlayerProfile findProfileById(String id) {
        return db.get(id);
    }

    @Override
    public PlayerProfile findProfileByUsername(String username) {
        return db.get(username);
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        db.put(profile.getId(), profile);
    }
}
