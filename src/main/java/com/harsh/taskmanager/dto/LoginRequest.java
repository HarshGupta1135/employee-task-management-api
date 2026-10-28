package com.harsh.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "admin@taskmanager.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(example = "Admin@123")
        @NotBlank(message = "Password is required")
        String password
) {
}
