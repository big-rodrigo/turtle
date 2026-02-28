package turtle.auth;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import turtle.auth.dto.AdminRegisterRequest;
import turtle.auth.dto.LoginRequest;
import turtle.auth.dto.RegisterRequest;
import turtle.auth.dto.TokenResponse;

@Tag(name = "Authentication", description = "Register and log in to obtain a JWT token")
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Operation(summary = "Register a new user (CLIENT or COACH)", description = "Creates a new CLIENT or COACH account. COACH accounts start with PENDING status and require admin approval.")
    @APIResponse(responseCode = "201", description = "User created; returns a JWT token",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @APIResponse(responseCode = "400", description = "Validation error or e-mail already in use")
    @POST
    @Path("/register")
    public Response register(@Valid RegisterRequest req) {
        TokenResponse token = authService.register(req);
        return Response.status(201).entity(token).build();
    }

    @Operation(summary = "Log in", description = "Authenticate with e-mail and password; returns a signed JWT.")
    @APIResponse(responseCode = "200", description = "Login successful; returns a JWT token",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @APIResponse(responseCode = "401", description = "Invalid credentials")
    @POST
    @Path("/login")
    public TokenResponse login(@Valid LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "Register an admin user", description = "Creates an ADMIN account. Requires a valid provisioning token set via the ADMIN_PROVISIONING_TOKEN environment variable.")
    @APIResponse(responseCode = "201", description = "Admin created; returns a JWT token",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @APIResponse(responseCode = "403", description = "Invalid or missing provisioning token")
    @POST
    @Path("/admin")
    public Response registerAdmin(@Valid AdminRegisterRequest req) {
        TokenResponse token = authService.registerAdmin(req);
        return Response.status(201).entity(token).build();
    }
}
