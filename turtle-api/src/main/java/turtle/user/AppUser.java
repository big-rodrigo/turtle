package turtle.user;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.Optional;

@Entity
@Table(name = "app_user")
public class AppUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(nullable = false, unique = true, length = 200)
    public String email;

    @Column(length = 30)
    public String phone;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public UserRole role;

    public static Optional<AppUser> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}
