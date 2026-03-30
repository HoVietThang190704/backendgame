package com.nhomgame.infrastructure.match;

import com.nhomgame.domain.match.MatchHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchHistoryRepository extends MongoRepository<MatchHistory, String> {
    List<MatchHistory> findByPlayersUserId(String userId);
    List<MatchHistory> findByMatchId(String matchId);
}
