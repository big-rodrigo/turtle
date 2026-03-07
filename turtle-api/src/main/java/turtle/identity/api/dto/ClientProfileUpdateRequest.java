package turtle.identity.api.dto;

import jakarta.validation.Valid;
import java.util.List;

public record ClientProfileUpdateRequest(
        String description,
        @Valid List<ClientSocialLinkRequest> socialLinks
) {}
