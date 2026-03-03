package turtle.coach;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.booking.Booking;
import turtle.user.AppUser;

import java.time.LocalDate;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_window_id")
    public TimeWindow timeWindow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    public Booking booking;

    public AvailabilityStatus status() {
        if (booking != null) return AvailabilityStatus.BOOKED;
        if (startsAt.isBefore(LocalDateTime.now())) return AvailabilityStatus.EXPIRED;
        return AvailabilityStatus.AVAILABLE;
    }

    public static List<Availability> findByCoachOnDate(Long coachId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return list("coach.id = ?1 AND startsAt >= ?2 AND startsAt < ?3 ORDER BY startsAt ASC",
                coachId, from, to);
    }

    public static List<Availability> findFreeByTimeWindow(Long timeWindowId) {
        return list("timeWindow.id = ?1 AND booking IS NULL AND startsAt > ?2",
                timeWindowId, LocalDateTime.now());
    }
}
