package pokergame.client.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import pokergame.GameContext;
import pokergame.domain.dto.LoginRequestDTO;
import pokergame.domain.dto.RegisterRequestDTO; // Create this record in poker-common
import pokergame.domain.dto.PlayerProfileDTO;

public class AuthNetworkClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverBaseUrl = "http://localhost:8080/api/auth";

    public  AuthNetworkClient() {
        this.httpClient  = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();;
        this.objectMapper.registerModule(new JavaTimeModule());
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
                // 1. EXTRACTION: Grab the Authorization header sent by Javalin
                response.headers().firstValue("Authorization").ifPresent(authHeader -> {
                    if (authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7); // Strip off "Bearer "

                        // 2. STORAGE: Save it to your global game state!
                        GameContext.setJwtToken(token);
                        System.out.println("[Auth Client] Successfully intercepted and saved JWT Token!");
                    }
                });

                // 3. PARSING: Read the profile JSON sent back by the Javalin server
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
            return response.statusCode() == 201; // 201 Created

        } catch (Exception e) {
            System.err.println("Registration network failure: " + e.getMessage());
            return false;
        }
    }
}