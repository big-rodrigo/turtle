package turtle.booking;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.coach.Availability;
import turtle.coach.CoachingService;
import turtle.user.AppUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking")
public class Booking extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    public AppUser client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "coach_id", nullable = false)
    public AppUser coach;

    @OneToMany(mappedBy = "booking", fetch = FetchType.EAGER)
    @OrderBy("startsAt ASC")
    public List<Availability> slots = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public BookingStatus status = BookingStatus.PENDING;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "booking_extras",
        joinColumns = @JoinColumn(name = "booking_id"),
        inverseJoinColumns = @JoinColumn(name = "service_id")
    )
    public List<CoachingService> extras = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    public String notes;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public LocalDateTime startsAt() {
        return slots.isEmpty() ? null : slots.get(0).startsAt;
    }

    public LocalDateTime endsAt() {
        return slots.isEmpty() ? null : slots.get(slots.size() - 1).endsAt;
    }

    public static List<Booking> findActiveByCoachOnDate(Long coachId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        List<BookingStatus> active = List.of(BookingStatus.PENDING, BookingStatus.APPROVED);
        return getEntityManager().createQuery(
                "SELECT DISTINCT b FROM Booking b JOIN b.slots s " +
                "WHERE b.coach.id = :coach AND s.startsAt >= :from AND s.startsAt < :to AND b.status IN :statuses",
                Booking.class)
                .setParameter("coach", coachId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("statuses", active)
                .getResultList();
    }
}
