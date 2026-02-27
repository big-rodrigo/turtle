package turtle.coach.dto;

import java.time.LocalDateTime;

public record AvailabilityRequest(LocalDateTime startsAt, LocalDateTime endsAt) {}
