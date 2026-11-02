package org.springy.som.nlp.judge;

/**
 * The model's decision. {@code verdict} is {@code supported}, {@code contradicted}, or {@code uncertain}.
 * {@code score} is confidence from 0 to 1.
 */
public record JudgeVerdict(String verdict, double score, String rationale) {
}
