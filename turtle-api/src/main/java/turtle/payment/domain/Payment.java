package turtle.payment.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.booking.domain.Booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@Table(name = "payment")
public class Payment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    public Booking booking;

    @Column(name = "preference_id")
    public String preferenceId;

    @Column(name = "external_payment_id")
    public String externalPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public PaymentStatus status = PaymentStatus.PENDING;

    @Column(precision = 10, scale = 2)
    public BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public static Optional<Payment> findByBookingId(Long bookingId) {
        return find("booking.id", bookingId).firstResultOptional();
    }

    public static Optional<Payment> findByExternalPaymentId(String externalPaymentId) {
        return find("externalPaymentId", externalPaymentId).firstResultOptional();
    }
}
