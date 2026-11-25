package org.springy.som.nlp.km.internal;

import java.util.List;

public record KmResult(boolean ok, String input, String answer, String error, String message,
                       boolean incomplete, List<String> errors, List<String> errorExpressions,
                       List<String> warnings, String text, String statistics) {

    static KmResult from(KmEngineBridge.Evaluation evaluation) {
        return new KmResult(evaluation.ok(), evaluation.input(), evaluation.answer(), evaluation.error(),
                evaluation.message(), evaluation.incomplete(), evaluation.errors(), evaluation.errorExpressions(),
                evaluation.warnings(), evaluation.text(), evaluation.statistics());
    }
}
