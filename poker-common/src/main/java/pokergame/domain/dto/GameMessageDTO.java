package pokergame.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameMessageDTO(
        @JsonProperty("type") String type,
        @JsonProperty("sender") String sender,
        @JsonProperty("payload") Object payload
) {
}