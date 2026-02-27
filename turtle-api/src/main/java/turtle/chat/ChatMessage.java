package turtle.chat;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.booking.Booking;
import turtle.user.AppUser;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "chat_message")
public class ChatMessage extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", nullable = false)
    public Booking booking;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    public AppUser sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String content;

    @Column(name = "sent_at", nullable = false)
    public LocalDateTime sentAt = LocalDateTime.now();

    public static List<ChatMessage> findByBooking(Long bookingId) {
        return list("booking.id = ?1 order by sentAt asc", bookingId);
    }
}
