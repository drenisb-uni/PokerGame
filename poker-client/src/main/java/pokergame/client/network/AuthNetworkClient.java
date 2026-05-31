package pokergame.client.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map; // Added for easy inline JSON serialization

import pokergame.GameContext;
import pokergame.domain.dto.LoginRequestDTO;
import pokergame.domain.dto.RegisterRequestDTO;
import pokergame.domain.dto.PlayerProfileDTO;

public class AuthNetworkClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverBaseUrl = "http://localhost:8080/api/auth";

    public AuthNetworkClient() {
        this.httpClient  = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Sends a password reset request to the backend auth service cluster.
     * Returns true if user exists and a temporary key was generated.
     */
    public boolean requestPasswordReset(String username) {
        try {
            // Leverage your existing objectMapper using a key-value map for safe JSON serialization
            Map<String, String> payload = Map.of("username", username);
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/forgot-password"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Forgot Password Status Code: " + response.statusCode());
            System.out.println("Server Reset Response Body: " + response.body());

            // Returns true if server returns a standard 200 OK acknowledgment
            return response.statusCode() == 200;

        } catch (Exception e) {
            System.err.println("Password reset network failure: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends credentials to server. Returns the PlayerProfileDTO if successful, null if failed.
     */
    public PlayerProfileDTO login(String username, String password) {
        try {
            LoginRequestDTO dto = new LoginRequestDTO(username, password);
            String jsonBody = objectMapper.writeValueAsString(dto);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("HTTP Status Code: " + response.statusCode());
            System.out.println("Server Response Body: " + response.body());

            if (response.statusCode() == 200) {
                response.headers().firstValue("Authorization").ifPresent(authHeader -> {
                    if (authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        GameContext.setJwtToken(token);
                        System.out.println("[Auth Client] Successfully intercepted and saved JWT Token!");
                    }
                });

                return objectMapper.readValue(response.body(), PlayerProfileDTO.class);
            }
        } catch (Exception e) {
            System.err.println("Login network failure: " + e.getMessage());
        }
        return null;
    }

    /**
     * Sends registration details to server. Returns true if successfully created.
     */
    public boolean register(String username, String email, String password) {
        try {
            RegisterRequestDTO dto = new RegisterRequestDTO(username, email, password);
            String jsonBody = objectMapper.writeValueAsString(dto);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 201;

        } catch (Exception e) {
            System.err.println("Registration network failure: " + e.getMessage());
            return false;
        }
    }
}