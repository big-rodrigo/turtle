package turtle.coaching.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.booking.domain.Booking;
import turtle.identity.domain.AppUser;

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

    public static List<Availability> findByCoachOnDateWithService(Long coachId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return getEntityManager().createQuery(
                "SELECT a FROM Availability a " +
                "LEFT JOIN FETCH a.timeWindow tw " +
                "LEFT JOIN FETCH tw.service " +
                "WHERE a.coach.id = :coach AND a.startsAt >= :from AND a.startsAt < :to " +
                "ORDER BY a.startsAt ASC",
                Availability.class)
                .setParameter("coach", coachId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    public static List<Availability> findByServiceOnDate(Long serviceId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return getEntityManager().createQuery(
                "SELECT a FROM Availability a " +
                "JOIN FETCH a.timeWindow tw " +
                "JOIN FETCH tw.service s " +
                "WHERE s.id = :serviceId " +
                "AND a.startsAt >= :from AND a.startsAt < :to " +
                "ORDER BY a.startsAt ASC",
                Availability.class)
                .setParameter("serviceId", serviceId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    public static List<Availability> findFreeByTimeWindow(Long timeWindowId) {
        return list("timeWindow.id = ?1 AND booking IS NULL AND startsAt > ?2",
                timeWindowId, LocalDateTime.now());
    }

    public static void deleteUnbookedFutureForCoachOnDate(Long coachId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        delete("coach.id = ?1 AND booking IS NULL AND startsAt >= ?2 AND startsAt < ?3 AND startsAt > ?4",
                coachId, from, to, LocalDateTime.now());
    }

    public static List<Availability> findBookedForCoachOnDate(Long coachId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return list("coach.id = ?1 AND booking IS NOT NULL AND startsAt >= ?2 AND startsAt < ?3",
                coachId, from, to);
    }
}
