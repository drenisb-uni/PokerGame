package pokergame.server;

import io.javalin.Javalin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.json.JavalinJackson;
import pokergame.server.bot.BotManager;

import pokergame.domain.dto.LoginRequestDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.dto.RegisterRequestDTO;
import pokergame.server.dbinfrastructure.HikariDSProvider;
import pokergame.server.dbinfrastructure.SqlGameRepository;
import pokergame.server.dbinfrastructure.SqlPlayerRepository;
import pokergame.server.domain.repository.IGameRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.network.PokerWebSocketServer;
import pokergame.server.service.ServerAuthService;

public class ServerApp {
    public static void main(String[] args) {
        // 1. Initialize core backend infrastructure
        HikariDSProvider dsProvider = new HikariDSProvider();
        IPlayerRepository playerRepository = new SqlPlayerRepository(dsProvider);
        IGameRepository gameRepository = new SqlGameRepository(dsProvider);
        ServerAuthService authService = new ServerAuthService(playerRepository);

        PokerGameEngine gameEngine = new PokerGameEngine(playerRepository, gameRepository);
        GameCommandProcessor commandProcessor = new GameCommandProcessor(gameEngine);
        BotManager botManager = new BotManager(commandProcessor, gameEngine);
        gameEngine.addObserver(botManager);

        // 2. Start the HTTP API Server for Secure Login/Registration (Port 8080)
        ObjectMapper httpJsonMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Javalin httpApp = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(httpJsonMapper, false));
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
        }).start(8080);

        // Define clean, stateless REST routes
        httpApp.post("/api/auth/login", ctx -> {
            System.out.println("\n[Route Debug] --- INCOMING LOGIN REQUEST ---");
            LoginRequestDTO credentials = ctx.bodyAsClass(LoginRequestDTO.class);

            System.out.println("[Route Debug] Parsed Username: " + credentials.username());

            // 1. Let's see what the database repository is actually pulling up!
            var fetchedProfile = playerRepository.findProfileByUsername(credentials.username());

            if (fetchedProfile == null) {
                System.out.println("[DB Inspect] ❌ Repository returned NULL! User does not exist in DB.");
            } else {
                String hashInDb = fetchedProfile.passwordHash();
                System.out.println("[DB Inspect] ✅ User found in DB!");
            }

            // 2. Proceed with normal authentication
            PlayerProfileDTO profile = authService.authenticatePlayer(credentials.username(), credentials.password());

            if (profile != null) {
                ctx.status(200).json(profile);
            } else {
                ctx.status(401).result("Invalid Credentials");
            }
        });

        httpApp.post("/api/auth/register", ctx -> {
            RegisterRequestDTO regData = ctx.bodyAsClass(RegisterRequestDTO.class);

            boolean created = authService.registerPlayer(regData.username(), regData.email(), regData.password());
            if (created) {
                ctx.status(201); // 201 Created
            } else {
                ctx.status(400).result("Username or email already exists");
            }
        });

        httpApp.get("/api/profile/{playerId}", ctx -> {
            String playerId = ctx.pathParam("playerId");
            PlayerProfileDTO profile = playerRepository.findProfileById(playerId);
            if (profile == null) {
                profile = playerRepository.findProfileByUsername(playerId);
            }

            if (profile == null) {
                ctx.status(404).result("Profile not found");
                return;
            }

            ctx.status(200).json(profile);
        });

        httpApp.get("/api/profile/{playerId}/recent-hands", ctx -> {
            String playerId = ctx.pathParam("playerId");
            int limit = 10;
            try {
                String requestedLimit = ctx.queryParam("limit");
                if (requestedLimit != null) {
                    limit = Integer.parseInt(requestedLimit);
                }
            } catch (NumberFormatException ignored) {
                limit = 10;
            }

            ctx.status(200).json(gameRepository.findRecentHandsForPlayer(playerId, Math.min(Math.max(limit, 1), 25)));
        });

        // 3. Start the Dedicated Game Loop WebSocket Server (Port 8081)
        PokerWebSocketServer wsServer = new PokerWebSocketServer(8081, commandProcessor, gameEngine, botManager);
        gameEngine.addObserver(wsServer);
        wsServer.start();

        System.out.println(">>> Server fully booted. Listening for HTTP on 8080, WebSockets on 8081 <<<");
    }
}
