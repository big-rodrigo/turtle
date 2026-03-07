package turtle.booking.domain.event;

import turtle.booking.domain.Booking;

public record BookingRejectedEvent(Booking booking) {}
