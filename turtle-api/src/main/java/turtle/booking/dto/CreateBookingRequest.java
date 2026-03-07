package turtle.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookingRequest(
        @NotNull @Size(min = 1) List<Long> availabilityIds,
        String notes,
        List<Long> extraServiceIds
) {}
