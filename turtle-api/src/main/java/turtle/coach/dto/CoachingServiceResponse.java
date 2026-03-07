package turtle.coach.dto;

import java.util.List;

public record CoachingServiceResponse(
        Long id,
        Long coachId,
        String name,
        String description,
        List<ExtraServiceSummary> extras
) {
    public record ExtraServiceSummary(Long id, String name, String description) {}
}
