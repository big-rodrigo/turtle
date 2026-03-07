package turtle.coaching.api.dto;

import java.util.List;

public record CoachResponse(
        Long id,
        String name,
        String specialty,
        String description,
        String pictureUrl,
        List<SocialLinkResponse> socialLinks
) {}
