package pokergame.server.service;

import io.javalin.http.Context;
import pokergame.domain.dto.LoginRequestDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.dto.RegisterRequestDTO;
import pokergame.engine.IPublicActionAPI;

public class HttpRouteService {
    private final ServerAuthService authService;
    private final TokenValidationService tokenValidationService;

    public HttpRouteService(ServerAuthService authService,  TokenValidationService tokenValidationService) {
        this.authService = authService;
        this.tokenValidationService = tokenValidationService;
    }

    public void handleLogin(Context ctx) {
        LoginRequestDTO credentials = ctx.bodyAsClass(LoginRequestDTO.class);
        PlayerProfileDTO profile = authService.authenticatePlayer(credentials.username(), credentials.password());

        if (profile != null) {
            String token = tokenValidationService.generateToken(profile.id(), profile.username());

            ctx.header("Authorization", "Bearer " + token);
            ctx.status(200).json(profile);
        } else {
            ctx.status(401).result("Invalid Credentials");
        }
    }

    public void handleRegister(Context ctx) {
        RegisterRequestDTO regData = ctx.bodyAsClass(RegisterRequestDTO.class);
        boolean created = authService.registerPlayer(regData.username(), regData.email(), regData.password());

        if (created) {
            ctx.status(201);
        } else {
            ctx.status(400).result("Username or email already exists");
        }
    }
}