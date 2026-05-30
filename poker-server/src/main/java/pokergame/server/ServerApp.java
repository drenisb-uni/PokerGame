package pokergame.server;

import io.javalin.Javalin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.json.JavalinJackson;

import pokergame.server.dbinfrastructure.HikariDSProvider;
import pokergame.server.dbinfrastructure.SqlPlayerRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.network.PokerWebSocketServer;
import pokergame.server.service.ServerAuthService;
import pokergame.server.service.HttpRouteService;
import pokergame.server.service.GameNetworkService;

public class ServerApp {
    public static void main(String[] args) {
        HikariDSProvider dsProvider = new HikariDSProvider();
        IPlayerRepository playerRepository = new SqlPlayerRepository(dsProvider);
        ServerAuthService authService = new ServerAuthService(playerRepository);

        PokerGameEngine gameEngine = new PokerGameEngine(playerRepository);
        GameCommandProcessor commandProcessor = new GameCommandProcessor(gameEngine);

        HttpRouteService httpRouteService = new HttpRouteService(authService);
        GameNetworkService gameNetworkService = new GameNetworkService(commandProcessor);

        Javalin httpApp = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.jsonMapper(new JavalinJackson());
        }).start(8080);

        httpApp.post("/api/auth/login", httpRouteService::handleLogin);
        httpApp.post("/api/auth/register", httpRouteService::handleRegister);

        // Start Dedicated Game WebSocket Server (Port 8081)
        // If your custom PokerWebSocketServer needs the gameNetworkService callbacks,
        // you pass it here, otherwise you configure its internal ws pathways to call gameNetworkService.

        PokerWebSocketServer wsServer = new PokerWebSocketServer(8081, commandProcessor);
        wsServer.start();

        System.out.println(">>> Server fully booted. Listening for HTTP on 8080, WebSockets on 8081 <<<");
    }
}