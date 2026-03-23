package com.nhomgame.service.match;

import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.dto.MatchFindRequest;

/**
 * Service interface for match finding operations
 */
public interface MatchService {
    /**
     * Add user to waiting queue to find a match
     * 
     * @param userId User ID from JWT
     * @param request MatchFindRequest with preferences
     * @return WaitingQueue entry
     * @throws IllegalArgumentException if user is already in a match or already waiting
     */
    WaitingQueue findMatch(String userId, MatchFindRequest request);

    /**
     * Get current waiting queue entry for user
     */
    WaitingQueue getWaitingQueueEntry(String userId);

    /**
     * Cancel waiting queue entry
     */
    void cancelWaitingQueue(String userId);
}
