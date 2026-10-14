package com.services.active.repository;

import com.services.active.models.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoutineRepository extends JpaRepository<Routine, UUID> {
    List<Routine> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Routine> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Routine r WHERE r.id = :id")
    Optional<Routine> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Routine r WHERE r.id IN :ids AND r.userId = :userId ORDER BY r.id")
    List<Routine> findAllByIdInAndUserIdForUpdate(
            @Param("ids") Collection<UUID> ids,
            @Param("userId") UUID userId);
    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
    void deleteByUserId(UUID userId);
}
