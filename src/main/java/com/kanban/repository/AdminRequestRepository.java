package com.kanban.repository;

import com.kanban.model.entity.AdminRequest;
import com.kanban.model.entity.User;
import com.kanban.model.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRequestRepository extends JpaRepository<AdminRequest, UUID> {
    Optional<AdminRequest> findByUserAndStatus(User user, RequestStatus status);
    Page<AdminRequest> findByStatus(RequestStatus status, Pageable pageable);
    Page<AdminRequest> findAll(Pageable pageable);
}
