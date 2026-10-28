package com.harsh.taskmanager.service;

import com.harsh.taskmanager.dto.PageResponse;
import com.harsh.taskmanager.dto.UserResponse;
import com.harsh.taskmanager.entity.Role;
import com.harsh.taskmanager.entity.User;
import com.harsh.taskmanager.exception.BadRequestException;
import com.harsh.taskmanager.exception.ResourceNotFoundException;
import com.harsh.taskmanager.repository.UserRepository;
import com.harsh.taskmanager.security.UserPrincipal;
import com.harsh.taskmanager.util.SortValidator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class UserService {

    static final Set<String> SORTABLE_PROPERTIES = Set.of("id", "fullName", "email", "role", "createdAt");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), SORTABLE_PROPERTIES);
        return PageResponse.from(userRepository.findAll(pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse updateRole(Long id, Role role, UserPrincipal currentUser) {
        if (id.equals(currentUser.getId())) {
            // Prevents an admin from accidentally locking themselves (and possibly everyone) out of admin features.
            throw new BadRequestException("You cannot change your own role");
        }
        User user = findUser(id);
        user.setRole(role);
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
