package com.nhomgame.service.match;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.dto.CreateMatchRequest;
import com.nhomgame.domain.match.dto.MatchFindRequest;
import com.nhomgame.domain.match.dto.MatchResultResponse;
import com.nhomgame.domain.match.dto.MatchHistoryDTO;
import com.nhomgame.domain.match.dto.MoveResult;

/**
 * Service interface for match operations
 */
public interface MatchService {

    /**
     * Get paginated match history for user
     */
    Page<MatchHistoryDTO> getMatchHistory(String userId, Pageable pageable);
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

    /**
     * Create a new private match/room
     * 
     * @param userId User ID of the host (from JWT)
     * @param request CreateMatchRequest with optional configurations
     * @return Created Match object with generated PIN code
     * @throws IllegalArgumentException if user is already in an active match
     */
    Match createMatch(String userId, CreateMatchRequest request);

    /**
     * Get match by ID
     */
    Match getMatchById(String matchId);

    /**
     * Get match by PIN code
     */
    Match getMatchByPin(String pinCode);

    /**
     * Get active match (waiting/preparation/playing) for user
     */
    Match getActiveMatchForUser(String userId);

    /**
     * Add player to a match
     */
    Match addPlayer(String matchId, String userId);

    /**
     * Set player ready status
     */
    Match setPlayerReady(String matchId, String userId, boolean ready);

    /**
     * Start a match
     */
    Match startMatch(String matchId);

    Match joinMatchWithPin(String userId, String username, String pinCode);
    
    /**
     * Apply a move
     */
    MoveResult applyMove(String matchId, String userId, int x, int y, String action);

    /**
     * Switch turn
     */
    void switchTurn(String matchId);

    /**
     * Mark player as disconnected
     */
    void markPlayerDisconnected(String matchId, String userId);

    /**
     * Leave a match
     */
    Match leaveMatch(String matchId, String userId);

    /**
     * Get match result by ID
     */
    MatchResultResponse getMatchResult(String matchId);
}
