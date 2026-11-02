package org.springy.som.nlp.judge;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * One judge purpose. Each document in the {@code judges} collection is one request type.
 */
@Data
@Document(collection = "judges")
public class RequestType {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String systemPrompt;
    private String userPrompt;
    private List<VerdictType> verdicts = new ArrayList<>();
    private ScoreRange score;
    private String rationale;
}
