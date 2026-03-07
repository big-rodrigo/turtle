package turtle.booking.api.dto;

import turtle.booking.domain.BookingStatus;
import turtle.coaching.api.dto.CoachingServiceResponse.ExtraServiceSummary;

import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        Long clientId,
        String clientName,
        Long coachId,
        String coachName,
        Long serviceId,
        String serviceName,
        List<Long> availabilityIds,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        BookingStatus status,
        String notes,
        LocalDateTime createdAt,
        List<ExtraServiceSummary> extras
) {}
