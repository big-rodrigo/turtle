package turtle.coach;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.user.AppUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "time_window")
public class TimeWindow extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    public AppUser coach;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    @Column(name = "daily_start_time", nullable = false)
    public LocalTime dailyStartTime;

    @Column(name = "daily_end_time", nullable = false)
    public LocalTime dailyEndTime;

    @Column(name = "unit_of_work_minutes", nullable = false)
    public int unitOfWorkMinutes;

    @Column(name = "price_per_unit")
    public BigDecimal pricePerUnit;

    @Column(name = "priority", nullable = false)
    public int priority = 0;

    public static List<TimeWindow> findByCoach(Long coachId) {
        return list("coach.id", coachId);
    }

    public static List<TimeWindow> findByCoachForDate(Long coachId, LocalDate date) {
        return list("coach.id = ?1 AND startDate <= ?2 AND endDate >= ?2", coachId, date);
    }
}
