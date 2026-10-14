package com.services.active.repository;

import com.services.active.models.Workout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {
    List<Workout> findAllByUserId(UUID userId);
    Optional<Workout> findByIdAndUserId(UUID id, UUID userId);
    List<Workout> findAllByIdInAndUserId(Collection<UUID> ids, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workout w WHERE w.id = :id")
    Optional<Workout> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workout w WHERE w.id IN :ids AND w.userId = :userId ORDER BY w.id")
    List<Workout> findAllByIdInAndUserIdForUpdate(
            @Param("ids") Collection<UUID> ids,
            @Param("userId") UUID userId);
    void deleteByUserId(UUID userId);
}
