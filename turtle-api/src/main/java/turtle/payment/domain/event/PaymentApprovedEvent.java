package turtle.payment.domain.event;

import turtle.booking.domain.Booking;
import turtle.payment.domain.Payment;

public record PaymentApprovedEvent(Booking booking, Payment payment) {}
