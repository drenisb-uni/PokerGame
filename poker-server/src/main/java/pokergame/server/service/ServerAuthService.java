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

    /**
     * Resets a player's password, prints the plain text version to the console,
     * and securely stores the hashed version inside the database.
     */
    public boolean resetPasswordToConsole(String username) {
        // 1. Use your exact repository method to verify the user exists
        var playerProfile = playerRepository.findProfileByUsername(username);
        if (playerProfile == null) {
            System.out.println("[Auth Server] Reset failed: User '" + username + "' does not exist.");
            return false;
        }

        // 2. Generate an 8-character temporary password string
        String temporaryPassword = UUID.randomUUID().toString().substring(0, 8) + "!1A";

        // 3. SECURITY FIX: Hash it using your project's PasswordHasher utility so it can actually log in!
        String newSecureHash = PasswordHasher.hashPassword(temporaryPassword);

        // 4. Persistence: Update the database using the new contract method
        boolean databaseUpdated = playerRepository.updatePasswordHash(username, newSecureHash);

        if (databaseUpdated) {
            System.out.println("=================================================");
            System.out.println("[PASSWORD RESET EVENT]");
            System.out.println("User: " + username);
            System.out.println("Temporary Password: " + temporaryPassword);
            System.out.println("=================================================");
            return true;
        }

        return false;
    }
}