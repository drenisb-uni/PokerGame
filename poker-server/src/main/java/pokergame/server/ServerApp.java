package pokergame.server;

import io.javalin.Javalin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.json.JavalinJackson;
import pokergame.server.bot.BotManager;

import pokergame.server.dbinfrastructure.HikariDSProvider;
import pokergame.server.dbinfrastructure.SqlGameRepository;
import pokergame.server.dbinfrastructure.SqlPlayerRepository;
import pokergame.server.domain.repository.IGameRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.GameEventBroadcaster;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.network.NetworkEventAdapter;
import pokergame.server.network.PokerWebSocketServer;
import pokergame.server.service.ServerAuthService;
import pokergame.server.service.HttpRouteService;
import pokergame.server.service.GameNetworkService;
import pokergame.server.service.TokenValidationService;

public class ServerApp {
    public static void main(String[] args) {
        HikariDSProvider dsProvider = new HikariDSProvider();
        IPlayerRepository playerRepository = new SqlPlayerRepository(dsProvider);
        IGameRepository gameRepository = new SqlGameRepository(dsProvider);
        ServerAuthService authService = new ServerAuthService(playerRepository);

        PokerGameEngine gameEngine = new PokerGameEngine(playerRepository, gameRepository);
        GameCommandProcessor commandProcessor = new GameCommandProcessor(gameEngine);
        BotManager botManager = new BotManager(commandProcessor, gameEngine);

        // 2. Start the HTTP API Server for Secure Login/Registration (Port 8080)
        ObjectMapper httpJsonMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TokenValidationService tokenValidationService = new TokenValidationService();
        HttpRouteService httpRouteService = new HttpRouteService(authService, tokenValidationService);
        GameNetworkService gameNetworkService = new GameNetworkService(commandProcessor);

        Javalin httpApp = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(httpJsonMapper, false));
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.jsonMapper(new JavalinJackson());
        }).start(8080);

        httpApp.post("/api/auth/login", httpRouteService::handleLogin);
        httpApp.post("/api/auth/register", httpRouteService::handleRegister);
        httpApp.post("/api/auth/forgot-password", httpRouteService::handleForgotPassword);

        PokerWebSocketServer wsServer = new PokerWebSocketServer(8081, commandProcessor, tokenValidationService, gameNetworkService);
        NetworkEventAdapter networkEventAdapter = new NetworkEventAdapter(wsServer);

        gameEngine.addObserver(botManager);
        gameEngine.addObserver(networkEventAdapter);

        wsServer.start();

        System.out.println(">>> Server fully booted. Listening for HTTP on 8080, WebSockets on 8081 <<<");
    }
}
