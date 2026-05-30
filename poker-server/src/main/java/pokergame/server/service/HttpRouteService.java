package pokergame.server.service;

import io.javalin.http.Context;
import pokergame.domain.dto.LoginRequestDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.dto.RegisterRequestDTO;

public class HttpRouteService {
    private final ServerAuthService authService;

    public HttpRouteService(ServerAuthService authService) {
        this.authService = authService;
    }

    public void handleLogin(Context ctx) {
        LoginRequestDTO credentials = ctx.bodyAsClass(LoginRequestDTO.class);
        PlayerProfileDTO profile = authService.authenticatePlayer(credentials.username(), credentials.password());

        if (profile != null) {
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