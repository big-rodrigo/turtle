package turtle.booking.dto;

import turtle.booking.BookingStatus;
import turtle.coach.dto.CoachingServiceResponse.ExtraServiceSummary;

import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        Long clientId,
        String clientName,
        Long coachId,
        String coachName,
        List<Long> availabilityIds,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        BookingStatus status,
        String notes,
        LocalDateTime createdAt,
        List<ExtraServiceSummary> extras
) {}
