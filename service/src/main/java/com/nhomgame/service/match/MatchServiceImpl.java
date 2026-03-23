package com.nhomgame.service.match;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.WaitingQueue.Preferences;
import com.nhomgame.domain.match.dto.MatchFindRequest;
import com.nhomgame.infrastructure.auth.UserRepository;
import com.nhomgame.infrastructure.match.WaitingQueueRepository;

/**
 * Service implementation for match finding operations
 */
@Service
public class MatchServiceImpl implements MatchService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final UserRepository userRepository;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchServiceImpl.class);

    public MatchServiceImpl(WaitingQueueRepository waitingQueueRepository, UserRepository userRepository) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.userRepository = userRepository;
    }

    @Override
    public WaitingQueue findMatch(String userId, MatchFindRequest request) {
        log.info("User {} requesting to find match with boardSize: {}", userId, request.getBoardSize());

        // 1. Get user by ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 2. Check if user is already in a match
        if (user.getCurrentMatchId() != null && !user.getCurrentMatchId().isEmpty()) {
            throw new IllegalArgumentException("User is already in an active match");
        }

        // 3. Check if already in waiting queue
        boolean alreadyWaiting = waitingQueueRepository.existsByUserIdAndStatus(userId, "waiting");
        if (alreadyWaiting) {
            log.warn("User {} is already waiting in queue", userId);
            WaitingQueue existing = waitingQueueRepository.findByUserIdAndStatus(userId, "waiting")
                    .orElse(null);
            if (existing != null) {
                return existing; // Return existing entry instead of creating duplicate
            }
        }

        // 4. Create waiting queue entry
        Preferences preferences = new Preferences(request.getBoardSize());
        WaitingQueue queueEntry = new WaitingQueue(userId, user.getRank(), preferences);
        queueEntry.setJoinedAt(Instant.now());

        WaitingQueue saved = waitingQueueRepository.save(queueEntry);
        log.info("User {} added to waiting queue: {}", userId, saved.getId());

        return saved;
    }

    @Override
    public WaitingQueue getWaitingQueueEntry(String userId) {
        return waitingQueueRepository.findByUserIdAndStatus(userId, "waiting")
                .orElse(null);
    }

    @Override
    public void cancelWaitingQueue(String userId) {
        waitingQueueRepository.deleteByUserId(userId);
        log.info("Cancelled waiting queue entry for user {}", userId);
    }
}
