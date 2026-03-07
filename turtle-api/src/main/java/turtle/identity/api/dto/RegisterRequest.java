package turtle.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import turtle.identity.domain.UserRole;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotBlank @Size(min = 6, message = "must be at least 6 characters") String password,
        @NotNull UserRole role
) {}
