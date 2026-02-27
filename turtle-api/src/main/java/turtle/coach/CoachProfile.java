package turtle.coach;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.user.AppUser;

import java.util.Optional;

@Entity
@Table(name = "coach_profile")
public class CoachProfile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    public AppUser user;

    @Column(columnDefinition = "TEXT")
    public String bio;

    @Column(length = 200)
    public String specialty;

    public static Optional<CoachProfile> findByUserId(Long userId) {
        return find("user.id", userId).firstResultOptional();
    }
}
