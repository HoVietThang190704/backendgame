package com.nhomgame.domain.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameBoard {

    private Integer hearts;
    private String currentItem;
    private List<Coordinate> bombs;
    private List<Coordinate> flags;
    private List<RevealedCell> revealed;
}
