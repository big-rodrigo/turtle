package turtle.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.mindrot.jbcrypt.BCrypt;
import turtle.auth.dto.LoginRequest;
import turtle.auth.dto.RegisterRequest;
import turtle.auth.dto.TokenResponse;
import turtle.coach.CoachProfile;
import turtle.user.AppUser;
import turtle.user.UserRole;

import java.time.Instant;

@ApplicationScoped
public class AuthService {

    @Transactional
    public TokenResponse register(RegisterRequest req) {
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

        return new TokenResponse(generateToken(user));
    }

    private String generateToken(AppUser user) {
        return Jwt.issuer("turtle-api")
                .subject(String.valueOf(user.id))
                .groups(user.role.name())
                .expiresAt(Instant.now().plusSeconds(86400))
                .sign();
    }
}
