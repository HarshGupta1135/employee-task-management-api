package com.harsh.taskmanager.controller;

import com.harsh.taskmanager.dto.AuthResponse;
import com.harsh.taskmanager.dto.LoginRequest;
import com.harsh.taskmanager.dto.RegisterRequest;
import com.harsh.taskmanager.dto.UserResponse;
import com.harsh.taskmanager.exception.ApiError;
import com.harsh.taskmanager.security.UserPrincipal;
import com.harsh.taskmanager.service.AuthService;
import com.harsh.taskmanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register, log in and inspect the current user")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Register a new employee account (role USER) and receive an access token")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in with email and password and receive a JWT access token")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the profile of the authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return userService.getUser(currentUser.getId());
    }
}
