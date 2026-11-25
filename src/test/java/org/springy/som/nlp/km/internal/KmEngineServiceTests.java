package org.springy.som.nlp.km.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KmEngineServiceTests {

    @Test
    void rejectsInvalidPlayersAndBlankExpressionsBeforeEvaluating() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        var service = new KmEngineService(bridge, knowledgeBase);

        for (String player : new String[]{null, "", " ", "Global", "*Global", "Player) (global-situation)", "a b"}) {
            assertThrows(IllegalArgumentException.class, () -> service.evaluatePlayer(player, "(1 + 2)"));
        }
        for (String expression : new String[]{null, "", " "}) {
            assertThrows(IllegalArgumentException.class, () -> service.evaluatePlayer("PlayerA", expression));
        }
        verifyNoInteractions(bridge);
    }

    @Test
    void playerReasoningFailureDiscardsCreationAndPartialState() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        var initial = mock(KmEngineBridge.Session.class);
        var created = mock(KmEngineBridge.Session.class);
        var partial = mock(KmEngineBridge.Session.class);
        when(knowledgeBase.startupSession()).thenReturn(initial);
        String declaration = "(in-situation *Global (*PlayerA has (instance-of (Som-Agentic-Player))))";
        when(bridge.evaluate(initial, declaration)).thenReturn(evaluation(created, true, "(*PlayerA)", List.of()));
        when(bridge.evaluateInSituation(created, "*PlayerA", "bad"))
                .thenReturn(evaluation(partial, true, "nil", List.of("Reasoning failed")));
        when(bridge.evaluate(initial, "next")).thenReturn(evaluation(initial, true, "(t)", List.of()));
        var service = new KmEngineService(bridge, knowledgeBase);

        assertEquals(List.of("Reasoning failed"), service.evaluatePlayer("PlayerA", "bad").errors());
        assertEquals("(t)", service.evaluate("next").answer());
        verify(bridge).evaluate(initial, "next");
    }

    @Test
    void playerCreationFailureStopsBeforeEvaluatingTheBody() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        var initial = mock(KmEngineBridge.Session.class);
        when(knowledgeBase.startupSession()).thenReturn(initial);
        when(bridge.evaluate(initial, "(in-situation *Global (*PlayerA has (instance-of (Som-Agentic-Player))))"))
                .thenReturn(evaluation(initial, true, "nil", List.of("Creation failed")));
        var service = new KmEngineService(bridge, knowledgeBase);

        assertEquals(List.of("Creation failed"), service.evaluatePlayer("PlayerA", "body").errors());
        verify(bridge, never()).evaluateInSituation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

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

    @Test
    void baselineStartsFromTheLoadedLibraryAndLeavesTheServiceSession() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        var startup = mock(KmEngineBridge.Session.class);
        var retained = mock(KmEngineBridge.Session.class);
        var derived = mock(KmEngineBridge.Session.class);
        var discarded = mock(KmEngineBridge.Session.class);
        when(knowledgeBase.startupSession()).thenReturn(startup);
        when(bridge.evaluate(startup, "(keep)"))
                .thenReturn(evaluation(retained, true, "(t)", List.of()));
        when(bridge.evaluate(startup, "(goal)"))
                .thenReturn(evaluation(derived, true, "(*Goal)", List.of()));
        when(bridge.evaluate(derived, "(the som-notch of *View)"))
                .thenReturn(evaluation(discarded, true, "(cautious)", List.of()));
        when(bridge.evaluate(retained, "(next)"))
                .thenReturn(evaluation(retained, true, "(t)", List.of()));
        var service = new KmEngineService(bridge, knowledgeBase);

        service.evaluate("(keep)");
        var answers = service.evaluateBaseline(List.of("(goal)", "(the som-notch of *View)"));
        var next = service.evaluate("(next)");

        assertEquals("(*Goal)", answers.get(0).answer());
        assertEquals("(cautious)", answers.get(1).answer());
        assertEquals("(t)", next.answer());
        verify(bridge).evaluate(retained, "(next)");
        assertSame(startup, knowledgeBase.startupSession());
    }

    @Test
    void baselineFailureDoesNotReplaceTheServiceSession() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        var startup = mock(KmEngineBridge.Session.class);
        var retained = mock(KmEngineBridge.Session.class);
        var partial = mock(KmEngineBridge.Session.class);
        when(knowledgeBase.startupSession()).thenReturn(startup);
        when(bridge.evaluate(startup, "(keep)"))
                .thenReturn(evaluation(retained, true, "(t)", List.of()));
        when(bridge.evaluate(startup, "(bad)"))
                .thenReturn(evaluation(partial, true, "nil", List.of("Reasoning failed")));
        when(bridge.evaluate(retained, "(next)"))
                .thenReturn(evaluation(retained, true, "(t)", List.of()));
        var service = new KmEngineService(bridge, knowledgeBase);

        service.evaluate("(keep)");
        assertEquals(List.of("Reasoning failed"), service.evaluateBaseline(List.of("(bad)")).get(0).errors());
        assertEquals("(t)", service.evaluate("(next)").answer());
        verify(bridge).evaluate(retained, "(next)");
    }

    @Test
    void baselineRejectsAnEmptyQuestionBeforeCallingTheEngine() {
        var bridge = mock(KmEngineBridge.class);
        var knowledgeBase = mock(KmKnowledgeBase.class);
        when(knowledgeBase.startupSession()).thenReturn(mock(KmEngineBridge.Session.class));
        var service = new KmEngineService(bridge, knowledgeBase);

        var failure = assertThrows(IllegalArgumentException.class, () -> service.evaluateBaseline(List.of(" (ok) ", " ")));

        assertEquals("Expression cannot be null or empty", failure.getMessage());
        verify(bridge, org.mockito.Mockito.never()).evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static KmEngineBridge.Evaluation evaluation(KmEngineBridge.Session next, boolean ok, String answer, List<String> errors) {
        return new KmEngineBridge.Evaluation(next, ok, null, answer, null, null, false, errors, List.of(), List.of(), null, null);
    }
}
