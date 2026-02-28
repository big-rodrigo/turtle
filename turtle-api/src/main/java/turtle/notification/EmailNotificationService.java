package turtle.notification;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import turtle.booking.Booking;
import turtle.chat.ChatMessage;

@ApplicationScoped
public class EmailNotificationService {

    @Inject
    Mailer mailer;

    public void sendBookingCreated(Booking b) {
        send(
            b.coach.email,
            "New booking request from " + b.client.name,
            "<p>You have a new booking request from <strong>" + b.client.name + "</strong>"
                + " for <strong>" + b.availability.startsAt + "</strong>.</p>"
                + "<p>Log in to approve or reject.</p>"
        );
    }

    public void sendBookingApproved(Booking b) {
        send(
            b.client.email,
            "Your session with " + b.coach.name + " is confirmed",
            "<p>Your session with <strong>" + b.coach.name + "</strong>"
                + " on <strong>" + b.availability.startsAt + "</strong>"
                + " has been <strong>APPROVED</strong>.</p>"
                + "<p>You can now chat with your coach.</p>"
        );
    }

    public void sendBookingRejected(Booking b) {
        send(
            b.client.email,
            "Booking request not accepted",
            "<p>Your booking request for <strong>" + b.availability.startsAt + "</strong>"
                + " was not accepted by the coach.</p>"
                + "<p>Please choose another available slot.</p>"
        );
    }

    public void sendChatMessage(ChatMessage msg) {
        Booking booking = msg.booking;
        boolean senderIsClient = msg.sender.id.equals(booking.client.id);
        String recipientEmail = senderIsClient ? booking.coach.email : booking.client.email;
        send(
            recipientEmail,
            "New message from " + msg.sender.name,
            "<p><strong>" + msg.sender.name + "</strong> wrote:</p>"
                + "<blockquote>" + escapeHtml(msg.content) + "</blockquote>"
        );
    }

    private void send(String to, String subject, String htmlBody) {
        if (to == null || to.isBlank()) {
            Log.warnf("Skipping email notification: recipient address is null or blank");
            return;
        }
        try {
            mailer.send(Mail.withHtml(to, subject, htmlBody));
        } catch (Exception e) {
            Log.warnf("Email notification failed for %s: %s", to, e.getMessage());
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
