package com.harsh.taskmanager.controller;

import com.harsh.taskmanager.dto.PageResponse;
import com.harsh.taskmanager.dto.UpdateRoleRequest;
import com.harsh.taskmanager.dto.UserResponse;
import com.harsh.taskmanager.exception.ApiError;
import com.harsh.taskmanager.security.UserPrincipal;
import com.harsh.taskmanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "Employee administration (ADMIN only)")
@ApiResponse(responseCode = "401", description = "Missing, invalid or expired token", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ApiError.class)))
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List employees", description = "Sortable by id, fullName, email, role or createdAt.")
    @ApiResponse(responseCode = "200", description = "A page of users")
    public PageResponse<UserResponse> getUsers(@ParameterObject @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return userService.getUsers(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an employee by id")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change an employee's role")
    @ApiResponse(responseCode = "200", description = "Role updated")
    @ApiResponse(responseCode = "400", description = "Invalid role, or attempt to change your own role", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse updateRole(@PathVariable Long id,
                                   @Valid @RequestBody UpdateRoleRequest request,
                                   @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal currentUser) {
        return userService.updateRole(id, request.role(), currentUser);
    }
}
