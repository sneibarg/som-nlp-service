package org.springy.som.nlp.km.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KmEngineServiceTests {

    @Test
    void discardsPartialStateWhenTheEngineRecordsReasoningErrors() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        var initial = mock(KmEngineBridge.Session.class);
        var partial = mock(KmEngineBridge.Session.class);
        when(knowledgeBase.startupSession()).thenReturn(initial);
        when(bridge.evaluate(initial, "invalid"))
                .thenReturn(new KmEngineBridge.Evaluation(partial, true, "invalid", "nil", null, null,
                        false, List.of("Reasoning failed"), List.of(), List.of(), null, null));
        when(bridge.evaluate(initial, "next"))
                .thenReturn(new KmEngineBridge.Evaluation(initial, true, "next", "(t)", null, null,
                        false, List.of(), List.of(), List.of(), null, null));
        var service = new KmEngineService(bridge, knowledgeBase);

        assertEquals(List.of("Reasoning failed"), service.evaluate("invalid").errors());
        assertEquals("(t)", service.evaluate("next").answer());
        verify(bridge).evaluate(initial, "next");
    }

    @Test
    void wrapsUnexpectedFailuresWithAStableMessage() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        var initial = mock(KmEngineBridge.Session.class);
        when(knowledgeBase.startupSession()).thenReturn(initial);
        var cause = new IllegalStateException("Internal engine detail");
        when(bridge.evaluate(initial, "query")).thenThrow(cause);
        var service = new KmEngineService(bridge, knowledgeBase);

        var failure = assertThrows(KmEngineException.class, () -> service.evaluate("query"));

        assertEquals("Knowledge base evaluation failed", failure.getMessage());
        assertEquals(cause, failure.getCause());
    }
}
