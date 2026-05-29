package pokergame.client.network;

public record OutboundActionPayload(String action, int amount) {}