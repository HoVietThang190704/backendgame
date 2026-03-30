package com.nhomgame.service.match;

import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.infrastructure.match.MatchRepository;
import com.nhomgame.infrastructure.match.WaitingQueueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for matchmaking logic, handling queue management and match creation.
 */
@Service
@Transactional
public class MatchmakingService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final MatchRepository matchRepository;

    public MatchmakingService(WaitingQueueRepository waitingQueueRepository, MatchRepository matchRepository) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.matchRepository = matchRepository;
    }

    /**
     * Attempts to join a queue for matchmaking. If an opponent is found, creates a new match.
     * If no opponent is available, adds the user to the waiting queue.
     *
     * @param userId the ID of the user joining the queue
     * @return a new Match if an opponent was found, null if user was added to waiting queue
     * @throws IllegalStateException if the user is already in an active match
     */
    public Match joinQueue(String userId) {

        // Step 1: Check if user is currently in a match with status PREPARATION or PLAYING
        List<String> activeStatuses = Arrays.asList("PREPARATION", "PLAYING");
        List<Match> userMatches = matchRepository.findByPlayersContainingAndStatusIn(userId, activeStatuses);

        if (!userMatches.isEmpty()) {
            throw new IllegalStateException("User is already in a match with status PREPARATION or PLAYING");
        }

        // Step 2: Find opponent using findFirstByUserIdIsNotOrderByJoinedAtAsc
        Optional<WaitingQueue> opponentOptional = waitingQueueRepository.findFirstByUserIdIsNotOrderByJoinedAtAsc(userId);

        if (opponentOptional.isEmpty()) {
            // Step 3a: No opponent found, create new WaitingQueue for this user
            WaitingQueue newQueue = new WaitingQueue();
            newQueue.setUserId(userId);
            newQueue.setStatus("waiting");
            newQueue.setJoinedAt(Instant.now());
            newQueue.setCreatedAt(Instant.now());

            waitingQueueRepository.save(newQueue);
            return null;
        }

        // Step 3b: Opponent found, proceed with match creation
        WaitingQueue opponent = opponentOptional.get();

        // Delete opponent from WaitingQueue
        waitingQueueRepository.delete(opponent);

        // Create new Match
        Match match = new Match();
        match.setMatchType("public");
        match.setPinCode(generatePublicMatchPin());
        match.setStatus("PREPARATION");
        match.setCreatedAt(Instant.now());
        match.setUpdatedAt(Instant.now());

        // Add both userIds to players list
        Match.Player player1 = new Match.Player(userId, userId);
        Match.Player player2 = new Match.Player(opponent.getUserId(), opponent.getUserId());

        match.getPlayers().add(player1);
        match.getPlayers().add(player2);

        // Initialize gameBoard Map using both userIds as keys and empty GameBoard objects as values
        Match.GameBoard gameBoard1 = createEmptyGameBoard();
        Match.GameBoard gameBoard2 = createEmptyGameBoard();

        match.getGameBoard().put(userId, gameBoard1);
        match.getGameBoard().put(opponent.getUserId(), gameBoard2);

        // Save Match to repository and return
        Match savedMatch = matchRepository.save(match);
        return savedMatch;
    }

    /**
     * Creates an empty GameBoard with default values.
     * Hearts initialized to 3, bombs, flags, and revealed lists initialized as empty.
     *
     * @return a new empty GameBoard instance
     */
    private Match.GameBoard createEmptyGameBoard() {
        Match.GameBoard gameBoard = new Match.GameBoard();
        gameBoard.setHearts(3);
        gameBoard.setBombs(new ArrayList<>());
        gameBoard.setFlags(new ArrayList<>());
        gameBoard.setRevealed(new ArrayList<>());
        return gameBoard;
    }

    private String generatePublicMatchPin() {
        final int maxAttempts = 10;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String pin = "PUB-" + UUID.randomUUID().toString().substring(0, 8);
            if (!matchRepository.existsByPinCode(pin)) {
                return pin;
            }
        }

        throw new IllegalStateException("Unable to generate unique pin code for public match");
    }
}
