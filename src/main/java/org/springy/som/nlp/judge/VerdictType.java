package org.springy.som.nlp.judge;

import lombok.Data;

/**
 * One allowed verdict on a request type.
 */
@Data
public class VerdictType {

    private String name;
    private String meaning;
}
