package turtle.admin.dto;

public record CoachStatusResponse(
        Long userId,
        String name,
        String email,
        String specialty,
        String status
) {}
