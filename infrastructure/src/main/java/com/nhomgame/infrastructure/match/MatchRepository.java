package com.nhomgame.infrastructure.match;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.nhomgame.domain.match.Match;

/**
 * Repository interface for Match persistence
 */
@Repository
public interface MatchRepository extends MongoRepository<Match, String> {
    /**
     * Find match by PIN code
     */
    Optional<Match> findByPinCode(String pinCode);

    /**
     * Find by host ID and status
     */
    List<Match> findByHostIdAndStatus(String hostId, String status);

    /**
     * Check if PIN code exists
     */
    boolean existsByPinCode(String pinCode);

    /**
     * Find active matches by host ID
     */
    Optional<Match> findByHostIdAndStatusIn(String hostId, List<String> statuses);
}
