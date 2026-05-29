package pokergame.server.utils;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    private static final int COST_FACTOR = 12;

    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank())
            throw new IllegalArgumentException("Password cannot be empty.");

        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(COST_FACTOR));
    }

    public static boolean verifyPassword(String plainPassword, String hashedPasswordFromDb) {
        if (plainPassword == null || hashedPasswordFromDb == null)
            return false;

        try {
            return BCrypt.checkpw(plainPassword, hashedPasswordFromDb);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid password hash format encountered: " + e.getMessage());
            return false;
        }
    }
}