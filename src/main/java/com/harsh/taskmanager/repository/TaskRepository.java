package com.harsh.taskmanager.repository;

import com.harsh.taskmanager.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /**
     * Loads assignee and creator in the same query to avoid the N+1 select problem when mapping a page of tasks.
     */
    @Override
    @EntityGraph(attributePaths = {"assignee", "createdBy"})
    Page<Task> findAll(Specification<Task> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"assignee", "createdBy"})
    Optional<Task> findWithUsersById(Long id);
}
