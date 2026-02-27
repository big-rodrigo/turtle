package turtle.coach.dto;

import java.time.LocalDateTime;

public record AvailabilityResponse(Long id, LocalDateTime startsAt, LocalDateTime endsAt, boolean booked) {}
