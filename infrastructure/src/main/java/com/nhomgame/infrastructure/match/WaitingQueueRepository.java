package com.nhomgame.infrastructure.match;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.nhomgame.domain.match.WaitingQueue;

/**
 * Repository interface for WaitingQueue persistence
 */
@Repository
public interface WaitingQueueRepository extends MongoRepository<WaitingQueue, String> {
    /**
     * Find waiting queue entry by userId and status
     */
    Optional<WaitingQueue> findByUserIdAndStatus(String userId, String status);

    /**
     * Delete waiting queue entry by userId
     */
    void deleteByUserId(String userId);

    /**
     * Check if user is in waiting queue
     */
    boolean existsByUserIdAndStatus(String userId, String status);

    /**
     * Find first waiting queue entry where userId is not the given userId,
     * ordered by joinedAt in ascending order
     */
    Optional<WaitingQueue> findFirstByUserIdIsNotOrderByJoinedAtAsc(String userId);
}
