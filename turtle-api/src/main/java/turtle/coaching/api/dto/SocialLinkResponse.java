package turtle.coaching.api.dto;

import turtle.shared.domain.SocialLinkType;

public record SocialLinkResponse(Long id, SocialLinkType type, String url, String label) {}
