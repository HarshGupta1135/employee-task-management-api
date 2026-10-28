package com.harsh.taskmanager.repository;

import com.harsh.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are normalised to lower case before they are stored, so an exact match is enough. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
