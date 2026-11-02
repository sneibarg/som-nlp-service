package org.springy.som.nlp.judge.internal;

import org.springy.som.nlp.judge.JudgeVerdict;

/**
 * The LLM call that turns a prepared prompt into a verdict.
 */
public interface VerdictModel {

    JudgeVerdict judge(String system, String user);
}
