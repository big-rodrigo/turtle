package turtle.coach;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.user.AppUser;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "availability")
public class Availability extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "coach_id", nullable = false)
    public AppUser coach;

    @Column(name = "starts_at", nullable = false)
    public LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    public LocalDateTime endsAt;

    @Column(nullable = false)
    public boolean booked = false;

    public static List<Availability> findFreeByCoach(Long coachId) {
        return list("coach.id = ?1 and booked = false and startsAt > ?2",
                coachId, LocalDateTime.now());
    }
}
