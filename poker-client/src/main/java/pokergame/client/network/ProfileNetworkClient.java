package pokergame.client.network;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import pokergame.domain.dto.PlayerHandResultDTO;
import pokergame.domain.dto.PlayerProfileDTO;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProfileNetworkClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverBaseUrl = "http://localhost:8080/api/profile";

    public ProfileNetworkClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public List<PlayerHandResultDTO> loadRecentHands(String playerId, int limit) {
        if (playerId == null || playerId.isBlank()) {
            return List.of();
        }

        try {
            String encodedPlayerId = URLEncoder.encode(playerId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/" + encodedPlayerId + "/recent-hands?limit=" + limit))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            System.err.println("Could not load recent hands: " + e.getMessage());
        }

        return List.of();
    }

    public PlayerProfileDTO loadProfile(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }

        try {
            String encodedPlayerId = URLEncoder.encode(playerId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverBaseUrl + "/" + encodedPlayerId))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), PlayerProfileDTO.class);
            }
        } catch (Exception e) {
            System.err.println("Could not load profile: " + e.getMessage());
        }

        return null;
    }
}
