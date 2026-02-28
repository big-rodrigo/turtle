package turtle.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mindrot.jbcrypt.BCrypt;
import turtle.auth.dto.AdminRegisterRequest;
import turtle.auth.dto.LoginRequest;
import turtle.auth.dto.RegisterRequest;
import turtle.auth.dto.TokenResponse;
import turtle.coach.CoachProfile;
import turtle.coach.CoachStatus;
import turtle.user.AppUser;
import turtle.user.UserRole;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    @ConfigProperty(name = "turtle.admin.provisioning-token")
    Optional<String> adminProvisioningToken;

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (req.role() == UserRole.ADMIN) {
            throw new WebApplicationException("Cannot self-register as ADMIN", 403);
        }
        if (AppUser.findByEmail(req.email()).isPresent()) {
            throw new WebApplicationException("Email already registered", 409);
        }

        AppUser user = new AppUser();
        user.name = req.name();
        user.email = req.email();
        user.phone = req.phone();
        user.passwordHash = BCrypt.hashpw(req.password(), BCrypt.gensalt());
        user.role = req.role();
        user.persist();

        if (req.role() == UserRole.COACH) {
            CoachProfile profile = new CoachProfile();
            profile.user = user;
            profile.persist();
        }

        return new TokenResponse(generateToken(user));
    }

    public TokenResponse login(LoginRequest req) {
        AppUser user = AppUser.findByEmail(req.email())
                .orElseThrow(() -> new WebApplicationException("Invalid credentials", 401));

        if (!BCrypt.checkpw(req.password(), user.passwordHash)) {
            throw new WebApplicationException("Invalid credentials", 401);
        }

        if (user.role == UserRole.COACH) {
            CoachProfile profile = CoachProfile.findByUserId(user.id).orElse(null);
            if (profile != null && profile.status == CoachStatus.REJECTED) {
                throw new WebApplicationException("Account has been rejected", 403);
            }
        }

        return new TokenResponse(generateToken(user));
    }

    @Transactional
    public TokenResponse registerAdmin(AdminRegisterRequest req) {
        String token = adminProvisioningToken.filter(t -> !t.isBlank()).orElse(null);
        if (token == null) {
            throw new WebApplicationException("Admin provisioning is disabled", 503);
        }
        if (!token.equals(req.provisioningToken())) {
            throw new WebApplicationException("Invalid provisioning token", 403);
        }
        if (AppUser.findByEmail(req.email()).isPresent()) {
            throw new WebApplicationException("Email already registered", 409);
        }

        AppUser user = new AppUser();
        user.name = req.name();
        user.email = req.email();
        user.passwordHash = BCrypt.hashpw(req.password(), BCrypt.gensalt());
        user.role = UserRole.ADMIN;
        user.persist();

        return new TokenResponse(generateToken(user));
    }

    private String generateToken(AppUser user) {
        String group = switch (user.role) {
            case CLIENT -> "CLIENT";
            case ADMIN  -> "ADMIN";
            case COACH  -> {
                Optional<CoachProfile> profile = CoachProfile.findByUserId(user.id);
                yield profile.map(p -> p.status == CoachStatus.APPROVED ? "COACH" : "COACH_PENDING")
                             .orElse("COACH_PENDING");
            }
        };
        return Jwt.issuer("turtle-api")
                .subject(String.valueOf(user.id))
                .groups(group)
                .expiresAt(Instant.now().plusSeconds(86400))
                .sign();
    }
}
