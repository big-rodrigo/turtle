package turtle.coach.dto;

import turtle.coach.AvailabilityStatus;

import java.time.LocalDateTime;

public record AvailabilityResponse(Long id, LocalDateTime startsAt, LocalDateTime endsAt, AvailabilityStatus status, Long serviceId, String serviceName) {}
