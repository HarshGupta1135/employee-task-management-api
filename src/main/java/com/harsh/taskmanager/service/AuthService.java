package com.harsh.taskmanager.service;

import com.harsh.taskmanager.dto.AuthResponse;
import com.harsh.taskmanager.dto.LoginRequest;
import com.harsh.taskmanager.dto.RegisterRequest;
import com.harsh.taskmanager.dto.UserResponse;
import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.User;
import com.harsh.taskmanager.exception.ConflictException;
import com.harsh.taskmanager.exception.ResourceNotFoundException;
import com.harsh.taskmanager.repository.UserRepository;
import com.harsh.taskmanager.security.JwtService;
import com.harsh.taskmanager.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Self-registration always creates a USER; only an admin can promote someone to ADMIN. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with email " + email + " already exists");
        }
        User user = userRepository.save(
                new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()), Role.USER));
        return issueToken(user);
    }

    /**
     * Verifies the credentials with Spring Security's {@link AuthenticationManager}. Wrong credentials raise
     * {@code BadCredentialsException}, which is returned as 401 without revealing whether the email exists.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normaliseEmail(request.email()), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(UserPrincipal.from(user));
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), UserResponse.from(user));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
