package turtle.coaching.api.dto;

import jakarta.validation.Valid;
import java.util.List;

public record CoachProfileUpdateRequest(
        String description,
        String specialty,
        String pictureUrl,
        @Valid List<SocialLinkRequest> socialLinks
) {}
