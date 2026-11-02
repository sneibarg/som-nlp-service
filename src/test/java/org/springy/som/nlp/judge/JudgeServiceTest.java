package org.springy.som.nlp.judge;

import org.junit.jupiter.api.Test;
import org.springy.som.nlp.judge.internal.JudgeUnavailableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JudgeServiceTest {

    private final JudgeService judgeService = new JudgeService();

    @Test
    void rejectsABlankText() {
        assertThrows(IllegalArgumentException.class, () -> judgeService.judge("  ", "A claim."));
    }

    @Test
    void rejectsABlankClaim() {
        assertThrows(IllegalArgumentException.class, () -> judgeService.judge("A text.", null));
    }

    @Test
    void waitsForAJudgeFromTheCollection() {
        JudgeUnavailableException failure = assertThrows(JudgeUnavailableException.class,
                () -> judgeService.judge("Sophia works at Stanford.", "Sophia works at Stanford."));

        assertEquals("Judges are defined in the judges collection", failure.getMessage());
    }
}
