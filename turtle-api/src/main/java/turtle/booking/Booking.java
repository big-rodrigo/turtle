package turtle.booking;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.coach.Availability;
import turtle.user.AppUser;

import java.time.LocalDateTime;

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

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "availability_id", nullable = false, unique = true)
    public Availability availability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public BookingStatus status = BookingStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    public String notes;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
