package turtle.booking.dto;

import turtle.booking.BookingStatus;

import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long clientId,
        String clientName,
        Long coachId,
        String coachName,
        LocalDateTime startsAt,
        BookingStatus status,
        String notes,
        LocalDateTime createdAt
) {}
