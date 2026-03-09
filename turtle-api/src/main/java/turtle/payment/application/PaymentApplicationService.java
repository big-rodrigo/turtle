package turtle.payment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import turtle.booking.domain.Booking;
import turtle.booking.domain.BookingStatus;
import turtle.payment.domain.CheckoutPreference;
import turtle.payment.domain.Payment;
import turtle.payment.domain.PaymentInfo;
import turtle.payment.domain.PaymentStatus;
import turtle.payment.domain.event.PaymentApprovedEvent;
import turtle.payment.domain.service.PaymentGateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@ApplicationScoped
public class PaymentApplicationService {

    @Inject
    PaymentGateway paymentGateway;

    @Inject
    Event<PaymentApprovedEvent> paymentApprovedEvent;

    @ConfigProperty(name = "mercadopago.notification-url")
    String notificationUrl;

    @Transactional
    public CheckoutPreference createPreference(Long bookingId, Long clientId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null)
            throw new WebApplicationException("Booking not found", 404);
        if (!booking.client.id.equals(clientId))
            throw new WebApplicationException("Forbidden", 403);
        if (booking.status != BookingStatus.PENDING_PAYMENT)
            throw new WebApplicationException("Booking is not awaiting payment", 409);

        BigDecimal amount = calculateAmount(booking);

        // Free session — skip payment, go directly to awaiting coach confirmation
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            booking.status = BookingStatus.AWAITING_COACH;
            return null;
        }

        // Upsert payment record
        Payment payment = Payment.findByBookingId(bookingId).orElseGet(() -> {
            Payment p = new Payment();
            p.booking = booking;
            p.amount = amount;
            p.persist();
            return p;
        });

        String itemTitle = "Sessão de coaching" + (booking.service != null ? " — " + booking.service.name : "");
        CheckoutPreference preference = paymentGateway.createPreference(
                bookingId, itemTitle, amount, booking.client.email, notificationUrl);

        payment.preferenceId = preference.preferenceId();
        payment.updatedAt = LocalDateTime.now();

        return preference;
    }

    @Transactional
    public void processWebhook(String type, String externalPaymentId) {
        if (!"payment".equals(type)) return;

        PaymentInfo info = paymentGateway.getPayment(externalPaymentId);
        if (info.externalReference() == null) return;

        Long bookingId;
        try {
            bookingId = Long.parseLong(info.externalReference());
        } catch (NumberFormatException e) {
            return;
        }

        Booking booking = Booking.findById(bookingId);
        if (booking == null) return;

        Payment payment = Payment.findByBookingId(bookingId).orElseGet(() -> {
            Payment p = new Payment();
            p.booking = booking;
            p.persist();
            return p;
        });

        payment.externalPaymentId = externalPaymentId;
        payment.updatedAt = LocalDateTime.now();

        switch (info.status()) {
            case "approved" -> {
                payment.status = PaymentStatus.APPROVED;
                payment.amount = info.amount();
                if (booking.status == BookingStatus.PENDING_PAYMENT) {
                    booking.status = BookingStatus.AWAITING_COACH;
                    paymentApprovedEvent.fire(new PaymentApprovedEvent(booking, payment));
                }
            }
            case "rejected" -> {
                payment.status = PaymentStatus.REJECTED;
                // Keep booking as PENDING_PAYMENT so client can retry
            }
            case "cancelled" -> {
                payment.status = PaymentStatus.CANCELLED;
                if (booking.status == BookingStatus.PENDING_PAYMENT) {
                    booking.status = BookingStatus.CANCELLED;
                    booking.slots.forEach(s -> s.booking = null);
                }
            }
            default -> {
                // in_process, pending, in_mediation — no action
            }
        }
    }

    public Payment getPaymentForBooking(Long bookingId) {
        return Payment.findByBookingId(bookingId).orElse(null);
    }

    @Transactional
    public void refundForBooking(Long bookingId) {
        Payment.findByBookingId(bookingId).ifPresent(p -> {
            if (p.status == PaymentStatus.APPROVED && p.externalPaymentId != null) {
                paymentGateway.refund(p.externalPaymentId);
                p.status = PaymentStatus.REFUNDED;
                p.updatedAt = LocalDateTime.now();
            }
        });
    }

    private BigDecimal calculateAmount(Booking booking) {
        return booking.slots.stream()
                .map(slot -> slot.timeWindow != null && slot.timeWindow.pricePerUnit != null
                        ? slot.timeWindow.pricePerUnit
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
