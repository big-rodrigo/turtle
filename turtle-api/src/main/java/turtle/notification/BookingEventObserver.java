package turtle.notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import turtle.booking.Booking;
import turtle.booking.event.BookingApprovedEvent;
import turtle.booking.event.BookingCreatedEvent;
import turtle.booking.event.BookingRejectedEvent;
import turtle.chat.event.ChatMessageSentEvent;
import turtle.chat.ChatMessage;

@ApplicationScoped
public class BookingEventObserver {

    @Inject
    NotificationService notifications;

    @Inject
    EmailNotificationService emailNotifications;

    void onCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) BookingCreatedEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.coach.phone,
                "New booking request from " + b.client.name
                        + " for " + b.availability.startsAt
                        + ". Log in to approve or reject.");
        emailNotifications.sendBookingCreated(b);
    }

    void onApproved(@Observes(during = TransactionPhase.AFTER_SUCCESS) BookingApprovedEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.client.phone,
                "Your session with " + b.coach.name
                        + " on " + b.availability.startsAt
                        + " has been APPROVED. You can now chat with your coach.");
        emailNotifications.sendBookingApproved(b);
    }

    void onRejected(@Observes(during = TransactionPhase.AFTER_SUCCESS) BookingRejectedEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.client.phone,
                "Your booking request on " + b.availability.startsAt
                        + " was not accepted. Please choose another slot.");
        emailNotifications.sendBookingRejected(b);
    }

    void onChatMessage(@Observes(during = TransactionPhase.AFTER_SUCCESS) ChatMessageSentEvent e) {
        ChatMessage msg = e.message();
        Booking booking = msg.booking;
        // Notify the other participant
        boolean senderIsClient = msg.sender.id.equals(booking.client.id);
        String recipientPhone = senderIsClient ? booking.coach.phone : booking.client.phone;
        notifications.send(recipientPhone, msg.sender.name + ": " + msg.content);
        emailNotifications.sendChatMessage(msg);
    }
}
