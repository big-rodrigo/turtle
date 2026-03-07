package turtle.identity.api.dto;

import turtle.shared.domain.SocialLinkType;

public record ClientSocialLinkResponse(Long id, SocialLinkType type, String url, String label) {}
