package turtle.infrastructure.notification;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import turtle.booking.domain.Booking;
import turtle.conversation.domain.ChatMessage;

@ApplicationScoped
public class EmailNotificationService {

    @Inject
    Mailer mailer;

    public void sendBookingCreated(Booking b) {
        send(
            b.client.email,
            "Solicitação de sessão recebida",
            "<p>Sua solicitação de sessão com <strong>" + b.coach.name + "</strong>"
                + " para <strong>" + b.startsAt() + "</strong> foi recebida.</p>"
                + "<p>Complete o pagamento para garantir sua vaga.</p>"
        );
    }

    public void sendPaymentApproved(Booking b) {
        send(
            b.coach.email,
            "Nova sessão paga — confirme sua presença",
            "<p>Você tem uma nova sessão paga de <strong>" + b.client.name + "</strong>"
                + " para <strong>" + b.startsAt() + "</strong>.</p>"
                + "<p>Entre no app para confirmar sua disponibilidade.</p>"
        );
    }

    public void sendBookingConfirmed(Booking b) {
        send(
            b.client.email,
            "Sessão confirmada com " + b.coach.name,
            "<p>Sua sessão com <strong>" + b.coach.name + "</strong>"
                + " em <strong>" + b.startsAt() + "</strong>"
                + " foi <strong>CONFIRMADA</strong>!</p>"
                + "<p>Você já pode conversar com seu coach.</p>"
        );
    }

    public void sendBookingRejected(Booking b) {
        send(
            b.client.email,
            "Sessão recusada — reembolso iniciado",
            "<p>Sua sessão para <strong>" + b.startsAt() + "</strong>"
                + " foi recusada pelo coach.</p>"
                + "<p>Um reembolso foi iniciado automaticamente.</p>"
        );
    }

    public void sendBookingCancelled(Booking b) {
        send(
            b.coach.email,
            "Sessão cancelada por " + b.client.name,
            "<p>A sessão com <strong>" + b.client.name + "</strong>"
                + " em <strong>" + b.startsAt() + "</strong>"
                + " foi cancelada pelo cliente.</p>"
        );
    }

    public void sendChatMessage(ChatMessage msg) {
        Booking booking = msg.booking;
        boolean senderIsClient = msg.sender.id.equals(booking.client.id);
        String recipientEmail = senderIsClient ? booking.coach.email : booking.client.email;
        send(
            recipientEmail,
            "Nova mensagem de " + msg.sender.name,
            "<p><strong>" + msg.sender.name + "</strong> escreveu:</p>"
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
