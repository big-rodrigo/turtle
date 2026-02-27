package turtle.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import turtle.auth.dto.LoginRequest;
import turtle.auth.dto.RegisterRequest;
import turtle.auth.dto.TokenResponse;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    public Response register(RegisterRequest req) {
        TokenResponse token = authService.register(req);
        return Response.status(201).entity(token).build();
    }

    @POST
    @Path("/login")
    public TokenResponse login(LoginRequest req) {
        return authService.login(req);
    }
}
