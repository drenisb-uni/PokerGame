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
    private static final long EXPIRATION_TIME_MS = 15 * 60 * 1000;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public TokenValidationService() {
        this.algorithm = Algorithm.HMAC256(SECRET_KEY);
        this.verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
    }

    public String generateToken(String playerId, String username) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(playerId)
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .sign(algorithm);
    }

    public String validateTokenAndGetPlayerId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getSubject();
        } catch (JWTVerificationException e) {
            System.err.println("JWT Verification failed: " + e.getMessage());
            return null;
        }
    }

    public String validateTokenAndGetUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            com.auth0.jwt.interfaces.DecodedJWT jwt = verifier.verify(token);

            return jwt.getClaim("username").asString();
        } catch (com.auth0.jwt.exceptions.JWTVerificationException e) {
            System.err.println("JWT Verification failed while fetching username: " + e.getMessage());
            return null;
        }
    }
}
