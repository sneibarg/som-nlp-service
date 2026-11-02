package org.springy.som.nlp.judge;

/**
 * A text and the claim to judge against it.
 */
public record JudgeRequest(String text, String claim) {
}
