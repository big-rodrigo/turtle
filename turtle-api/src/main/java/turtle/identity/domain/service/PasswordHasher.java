package turtle.identity.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class PasswordHasher {

    public String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt());
    }

    public boolean verify(String raw, String hash) {
        return BCrypt.checkpw(raw, hash);
    }
}
