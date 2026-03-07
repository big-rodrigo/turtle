package turtle.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CoachingServiceRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        List<Long> extraServiceIds
) {}
