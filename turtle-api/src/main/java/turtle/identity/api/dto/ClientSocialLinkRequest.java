package turtle.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import turtle.shared.domain.SocialLinkType;

public record ClientSocialLinkRequest(
        @NotNull SocialLinkType type,
        @NotBlank String url,
        String label
) {}
