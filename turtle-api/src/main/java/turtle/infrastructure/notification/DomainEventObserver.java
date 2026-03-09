package turtle.infrastructure.notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import turtle.booking.domain.Booking;
import turtle.booking.domain.event.BookingCancelledEvent;
import turtle.booking.domain.event.BookingConfirmedEvent;
import turtle.booking.domain.event.BookingCreatedEvent;
import turtle.booking.domain.event.BookingRejectedEvent;
import turtle.conversation.domain.ChatMessage;
import turtle.conversation.domain.event.ChatMessageSentEvent;
import turtle.payment.domain.event.PaymentApprovedEvent;

@ApplicationScoped
public class DomainEventObserver {

    @Inject
    WhatsAppNotificationService notifications;

    @Inject
    EmailNotificationService emailNotifications;

    void onCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) BookingCreatedEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.client.phone,
                "Sua solicitação de sessão com " + b.coach.name
                        + " para " + b.startsAt()
                        + " foi recebida. Complete o pagamento para confirmar.");
        emailNotifications.sendBookingCreated(b);
    }

    void onPaymentApproved(@Observes(during = TransactionPhase.AFTER_SUCCESS) PaymentApprovedEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.coach.phone,
                "Nova sessão paga de " + b.client.name
                        + " para " + b.startsAt()
                        + ". Confirme sua disponibilidade no app.");
        emailNotifications.sendPaymentApproved(b);
    }

    void onConfirmed(@Observes(during = TransactionPhase.AFTER_SUCCESS) BookingConfirmedEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.client.phone,
                "Sua sessão com " + b.coach.name
                        + " em " + b.startsAt()
                        + " foi CONFIRMADA! Você já pode conversar com seu coach.");
        emailNotifications.sendBookingConfirmed(b);
    }

    void onRejected(@Observes(during = TransactionPhase.AFTER_SUCCESS) BookingRejectedEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.client.phone,
                "Sua sessão em " + b.startsAt()
                        + " foi recusada pelo coach. Um reembolso foi iniciado.");
        emailNotifications.sendBookingRejected(b);
    }

    void onCancelled(@Observes(during = TransactionPhase.AFTER_SUCCESS) BookingCancelledEvent e) {
        Booking b = e.booking();
        notifications.send(
                b.coach.phone,
                "A sessão com " + b.client.name
                        + " em " + b.startsAt()
                        + " foi cancelada pelo cliente.");
        emailNotifications.sendBookingCancelled(b);
    }

    void onChatMessage(@Observes(during = TransactionPhase.AFTER_SUCCESS) ChatMessageSentEvent e) {
        ChatMessage msg = e.message();
        Booking booking = msg.booking;
        boolean senderIsClient = msg.sender.id.equals(booking.client.id);
        String recipientPhone = senderIsClient ? booking.coach.phone : booking.client.phone;
        notifications.send(recipientPhone, msg.sender.name + ": " + msg.content);
        emailNotifications.sendChatMessage(msg);
    }
}
