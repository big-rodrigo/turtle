package turtle.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import turtle.booking.Booking;
import turtle.booking.BookingStatus;
import turtle.chat.event.ChatMessageSentEvent;
import turtle.user.AppUser;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ChatService {

    @Inject
    Event<ChatMessageSentEvent> chatMessageEvent;

    public List<ChatMessage> listMessages(Long bookingId, Long callerId) {
        getApprovedBookingForParticipant(bookingId, callerId);
        return ChatMessage.findByBooking(bookingId);
    }

    @Transactional
    public ChatMessage sendMessage(Long bookingId, Long senderId, String content) {
        Booking booking = getApprovedBookingForParticipant(bookingId, senderId);
        AppUser sender = AppUser.findById(senderId);

        ChatMessage msg = new ChatMessage();
        msg.booking = booking;
        msg.sender = sender;
        msg.content = content;
        msg.sentAt = LocalDateTime.now();
        msg.persist();

        chatMessageEvent.fire(new ChatMessageSentEvent(msg));
        return msg;
    }

    private Booking getApprovedBookingForParticipant(Long bookingId, Long userId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new WebApplicationException("Booking not found", 404);
        }
        if (booking.status != BookingStatus.APPROVED) {
            throw new WebApplicationException("Chat is only available for approved bookings", 403);
        }
        if (!booking.client.id.equals(userId) && !booking.coach.id.equals(userId)) {
            throw new WebApplicationException("Forbidden", 403);
        }
        return booking;
    }
}
