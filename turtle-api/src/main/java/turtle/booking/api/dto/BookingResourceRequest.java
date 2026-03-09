package turtle.booking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record BookingResourceRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @URL @Size(max = 2048) String url,
        String description
) {}
