package turtle.booking.api.dto;

import java.time.LocalDateTime;

public record BookingResourceResponse(
        Long id,
        String title,
        String url,
        String description,
        LocalDateTime createdAt
) {}
