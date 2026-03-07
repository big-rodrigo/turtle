package turtle.coaching.api.dto;

import turtle.coaching.domain.AvailabilityStatus;

import java.time.LocalDateTime;

public record AvailabilityResponse(Long id, LocalDateTime startsAt, LocalDateTime endsAt, AvailabilityStatus status, Long serviceId, String serviceName) {}
