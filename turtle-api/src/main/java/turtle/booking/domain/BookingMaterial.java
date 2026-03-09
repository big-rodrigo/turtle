package turtle.booking.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking_resource")
public class BookingMaterial extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    public Booking booking;

    @Column(nullable = false, length = 255)
    public String title;

    @Column(nullable = false, length = 2048)
    public String url;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
