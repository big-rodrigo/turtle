package turtle.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull Long availabilityId,
        String notes
) {}
