package turtle.coach.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AvailabilityRequest(
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt
) {}
