package com.nhomgame.domain.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "waitingqueues")
public class WaitingQueue {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    private int rank;

    private String status = "waiting";

    private Preferences preferences;

    private Instant joinedAt;

    private Instant createdAt;

    public WaitingQueue(String userId, int rank, Preferences preferences) {
        this.userId = userId;
        this.rank = rank;
        this.status = "waiting";
        this.preferences = preferences;
        this.createdAt = Instant.now();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Preferences {
        private String boardSize;
    }
}
