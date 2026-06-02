package pokergame.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsContext;
import pokergame.domain.dto.GameMessageDTO;
import pokergame.engine.commands.PlayerCommand;
import pokergame.server.engine.actor.TableActor;

public class GameNetworkService {
    private final PokerTableManager tableManager;
    private final ObjectMapper mapper;
    private final TableActor tableActor;

    public GameNetworkService(TableActor tableActor) {
        this.tableManager = new PokerTableManager();
        this.tableActor = tableActor;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public void handleConnect(WsContext ctx) {
        String username = ctx.queryParam("user");
        if (username != null && !username.isBlank()) {
            tableManager.addPlayer(username, ctx);
            System.out.println("[NetworkService] Player sat down: " + username);
        } else {
            ctx.session.close(1008, "Missing username parameter");
        }
    }

    public void handleMessage(String sender, String rawJsonPayload) {
        try {
            if (sender == null || rawJsonPayload == null || rawJsonPayload.isBlank()) {
                System.err.println("[NetworkService] Missing sender context or payload is empty.");
                return;
            }

            // 1. Manually deserialize the JSON string into the GameMessageDTO record
            GameMessageDTO incomingMessage = mapper.readValue(rawJsonPayload, GameMessageDTO.class);

            System.out.println("[NetworkService] Action " + incomingMessage.type() + " received from " + sender);

            // 2. Transform the network packet into an executable PlayerCommand
            PlayerCommand command = PlayerCommand.fromNetworkMessage(sender, incomingMessage);

            // 3. Queue it up for the engine thread

        } catch (IllegalArgumentException e) {
            System.err.println("[NetworkService] Malformed game action: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[NetworkService] Jackson failed to parse message: " + rawJsonPayload);
            e.printStackTrace();
        }
    }
    public void handleDisconnect(WsContext ctx) {
        String username = ctx.queryParam("user");
        if (username != null) {
            tableManager.removePlayer(username);
            System.out.println("[NetworkService] Player disconnected: " + username);
        }
    }

    public void handleError(WsContext ctx) {
        System.err.println("[NetworkService] WS Error encountered for user: " + ctx.queryParam("user"));
    }
}