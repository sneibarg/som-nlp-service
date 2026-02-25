package org.springy.som.nlp.nlp.dto;


import lombok.Data;
import java.util.List;

@Data
public class SynsetDTO {
    private String id;
    private String definition;
    private List<String> hypernyms;
    private List<String> synonyms;
}

