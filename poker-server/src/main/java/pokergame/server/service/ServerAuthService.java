package pokergame.server.service;

import pokergame.server.dbinfrastructure.SqlPlayerRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.server.utils.PasswordHasher;

import java.time.LocalDateTime;
import java.util.UUID;

public class ServerAuthService {
    private final SqlPlayerRepository playerRepository;

    public ServerAuthService(IPlayerRepository playerRepository) {
        this.playerRepository = (SqlPlayerRepository) playerRepository;
    }
    public PlayerProfileDTO authenticatePlayer(String username, String rawPassword) {
        // 1. Guard against bad incoming HTTP JSON data
        if (username == null || rawPassword == null) {
            System.err.println("[Auth Error] Login attempt failed: username or password payload was null.");
            return null;
        }

        // 2. Fetch the profile
        PlayerProfileDTO profile = playerRepository.findProfileByUsername(username);

        // 3. Guard against non-existent users
        if (profile == null) {
            System.out.println("[Auth Log] Login failed: User '" + username + "' not found in DB.");
            return null;
        }

        // 4. Extract the hash and GUARD against bad database mapping
        String storedHash = profile.passwordHash();
        if (storedHash == null) {
            System.err.println("[CRITICAL DB ERROR] Profile found for '" + username +
                    "', but the database returned a NULL password hash! " +
                    "Check your SqlPlayerRepository ResultSet mapping.");
            return null;
        }

        boolean isPasswordCorrect = PasswordHasher.verifyPassword(rawPassword, storedHash);

        if (isPasswordCorrect) {
            System.out.println("[Auth Log] ✅ Success! Password matches for user: " + username);
            return profile; // Return the loaded data to pass to the client!
        } else {
            System.out.println("[Auth Log] ❌ Failure: Password mismatch for user: " + username);
            return null;
        }
    }

    public boolean registerPlayer(String username, String email, String rawPassword) {
        if (playerRepository.findProfileByUsername(username) != null) {
            return false; // Username already taken
        }

        String newId = UUID.randomUUID().toString();
        String secureHash = PasswordHasher.hashPassword(rawPassword);

        PlayerProfileDTO secureProfile = new PlayerProfileDTO(newId, username, email, secureHash, 1000, LocalDateTime.now());
        playerRepository.saveProfile(secureProfile);

        System.out.println("Successfully registered new secure user profile: " + username);
        return true;
    }
}