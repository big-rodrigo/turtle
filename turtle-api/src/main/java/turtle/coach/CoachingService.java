package turtle.coach;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.user.AppUser;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "coaching_service")
public class CoachingService extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    public AppUser coach;

    @Column(nullable = false, length = 200)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "service_extras",
        joinColumns = @JoinColumn(name = "service_id"),
        inverseJoinColumns = @JoinColumn(name = "extra_id")
    )
    public List<CoachingService> extras = new ArrayList<>();

    public static List<CoachingService> findByCoach(Long coachId) {
        return list("coach.id", coachId);
    }

    public static boolean isUsedAsExtra(Long serviceId) {
        return count("SELECT COUNT(s) FROM CoachingService s JOIN s.extras e WHERE e.id = ?1", serviceId) > 0;
    }
}
