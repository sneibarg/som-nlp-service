package org.springy.som.nlp.judge;

import lombok.Data;

/**
 * How a request type uses its score.
 */
@Data
public class ScoreRange {

    private Double min;
    private Double max;
    private String meaning;
}
