package org.springy.som.nlp.judge;

import org.springy.som.nlp.judge.internal.JudgeUnavailableException;
import org.springframework.stereotype.Service;

/**
 * Entry point for LLM-as-a-Judge. Request types are documents in the MongoDB collection {@code judges}.
 */
@Service
public class JudgeService {

    /**
     * Judges a claim against a text using a request type defined in the judges collection.
     * @param text the source text
     * @param claim the statement to judge
     * @return the model's verdict, confidence, and rationale
     */
    public JudgeVerdict judge(String text, String claim) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("Claim cannot be null or empty");
        }
        throw new JudgeUnavailableException("Judges are defined in the judges collection");
    }
}
