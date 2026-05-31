package pokergame.server.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class TokenValidationService {

    // In production, load this from an environment variable! Never hardcode secrets.
    private static final String SECRET_KEY = System.getenv().getOrDefault("JWT_SECRET", "super-secure-poker-secret-key-2026");
    private static final String ISSUER = "PokerEngineBackend";

    // Tokens expire after 15 minutes to prevent replay attacks if a log leaks
    private static final long EXPIRATION_TIME_MS = 15 * 60 * 1000;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public TokenValidationService() {
        this.algorithm = Algorithm.HMAC256(SECRET_KEY);
        this.verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
    }

    /**
     * Generates a token after a successful Javalin HTTP login.
     */
    public String generateToken(String playerId, String username) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(playerId)
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .sign(algorithm);
    }

    /**
     * Validates the token sent by the WebSocket client on connection.
     * @return The verified Player ID (Subject) if valid, or null if validation fails.
     */
    public String validateTokenAndGetPlayerId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            // Verifier automatically checks expiration (exp) and issuer (iss)
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject(); // This returns your clean playerId
        } catch (JWTVerificationException e) {
            System.err.println("JWT Verification failed: " + e.getMessage());
            return null; // Implicitly rejects connection handshake
        }
    }
}
