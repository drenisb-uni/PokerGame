package pokergame.server;

import io.javalin.Javalin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.json.JavalinJackson;
import pokergame.server.bot.BotManager;

import pokergame.server.dbinfrastructure.HikariDSProvider;
import pokergame.server.dbinfrastructure.SqlPlayerRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.GameEventBroadcaster;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.engine.actor.TableActor;
import pokergame.server.network.NetworkEventAdapter;
import pokergame.server.network.PokerWebSocketServer;
import pokergame.server.service.*;

public class ServerApp {
    public static void main(String[] args) {
        HikariDSProvider dsProvider = new HikariDSProvider();
        IPlayerRepository playerRepository = new SqlPlayerRepository(dsProvider);
        ServerAuthService authService = new ServerAuthService(playerRepository);

        LobbyManager lobbyManager = new LobbyManager(playerRepository);

        TokenValidationService tokenValidationService = new TokenValidationService();
        HttpRouteService httpRouteService = new HttpRouteService(authService, tokenValidationService);

        Javalin httpApp = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.jsonMapper(new JavalinJackson());
        }).start(8080);

        httpApp.post("/api/auth/login", httpRouteService::handleLogin);
        httpApp.post("/api/auth/register", httpRouteService::handleRegister);

        PokerWebSocketServer wsServer = new PokerWebSocketServer(8081, tokenValidationService, lobbyManager);
        lobbyManager.setWebSocketServer(wsServer);

        wsServer.start();

        System.out.println(">>> Server fully booted. Listening for HTTP on 8080, WebSockets on 8081 <<<");
    }
}
