package turtle.auth.dto;

import turtle.user.UserRole;

public record RegisterRequest(
        String name,
        String email,
        String phone,
        String password,
        UserRole role
) {}
