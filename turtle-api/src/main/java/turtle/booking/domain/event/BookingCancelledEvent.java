package turtle.booking.domain.event;

import turtle.booking.domain.Booking;

public record BookingCancelledEvent(Booking booking) {}
