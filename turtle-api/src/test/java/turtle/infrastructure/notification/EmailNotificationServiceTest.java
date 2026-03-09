package turtle.infrastructure.notification;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import turtle.booking.domain.Booking;
import turtle.coaching.domain.Availability;
import turtle.conversation.domain.ChatMessage;
import turtle.identity.domain.AppUser;
import turtle.identity.domain.UserRole;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EmailNotificationServiceTest {

    @Inject
    MockMailbox mailbox;

    @Inject
    EmailNotificationService emailNotifications;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    @Test
    void sendBookingCreatedSendsEmailToClient() {
        emailNotifications.sendBookingCreated(buildBooking());

        List<io.quarkus.mailer.Mail> messages = mailbox.getMessagesSentTo("client@example.com");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getSubject().contains("recebida"));
    }

    @Test
    void sendPaymentApprovedSendsEmailToCoach() {
        emailNotifications.sendPaymentApproved(buildBooking());

        List<io.quarkus.mailer.Mail> messages = mailbox.getMessagesSentTo("coach@example.com");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getSubject().contains("confirme"));
    }

    @Test
    void sendBookingConfirmedSendsEmailToClient() {
        emailNotifications.sendBookingConfirmed(buildBooking());

        List<io.quarkus.mailer.Mail> messages = mailbox.getMessagesSentTo("client@example.com");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getSubject().contains("confirmada"));
    }

    @Test
    void sendBookingRejectedSendsEmailToClient() {
        emailNotifications.sendBookingRejected(buildBooking());

        List<io.quarkus.mailer.Mail> messages = mailbox.getMessagesSentTo("client@example.com");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getSubject().contains("recusada"));
    }

    @Test
    void sendChatMessageSendsEmailToOtherParticipant() {
        Booking booking = buildBooking();
        ChatMessage msg = new ChatMessage();
        msg.booking = booking;
        msg.sender = booking.client;
        msg.content = "Hello coach!";

        emailNotifications.sendChatMessage(msg);

        // sender is client, so coach should receive the email
        List<io.quarkus.mailer.Mail> messages = mailbox.getMessagesSentTo("coach@example.com");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getSubject().contains("Nova mensagem"));
    }

    @Test
    void sendSkipsWhenEmailIsNull() {
        Booking booking = buildBooking();
        booking.client.email = null;

        emailNotifications.sendBookingCreated(booking);

        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void sendSkipsWhenEmailIsBlank() {
        Booking booking = buildBooking();
        booking.client.email = "  ";

        emailNotifications.sendBookingCreated(booking);

        assertEquals(0, mailbox.getTotalMessagesSent());
    }

    @Test
    void htmlContentIsEscapedInChatMessages() {
        Booking booking = buildBooking();
        ChatMessage msg = new ChatMessage();
        msg.booking = booking;
        msg.sender = booking.client;
        msg.content = "<script>alert('xss')</script>";

        emailNotifications.sendChatMessage(msg);

        List<io.quarkus.mailer.Mail> messages = mailbox.getMessagesSentTo("coach@example.com");
        assertEquals(1, messages.size());
        String html = messages.get(0).getHtml();
        assertTrue(html.contains("&lt;script&gt;"), "HTML should be escaped");
        assertFalse(html.contains("<script>"), "Raw script tag must not appear in HTML body");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Booking buildBooking() {
        AppUser client = new AppUser();
        client.id = 1L;
        client.name = "Alice";
        client.email = "client@example.com";
        client.phone = "5511111111111";
        client.role = UserRole.CLIENT;

        AppUser coach = new AppUser();
        coach.id = 2L;
        coach.name = "Bob";
        coach.email = "coach@example.com";
        coach.phone = "5522222222222";
        coach.role = UserRole.COACH;

        Availability slot = new Availability();
        slot.startsAt = LocalDateTime.of(2026, 3, 10, 14, 0);
        slot.endsAt = LocalDateTime.of(2026, 3, 10, 15, 0);

        Booking b = new Booking();
        b.client = client;
        b.coach = coach;
        b.slots = new ArrayList<>(List.of(slot));
        return b;
    }
}
