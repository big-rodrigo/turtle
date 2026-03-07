package turtle.identity.api.dto;

import java.util.List;

public record ClientProfileResponse(
        Long id,
        String name,
        String description,
        List<ClientSocialLinkResponse> socialLinks
) {}
