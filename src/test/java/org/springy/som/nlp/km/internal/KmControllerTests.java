package org.springy.som.nlp.km.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KmController.class)
class KmControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KmEngineService kmEngine;

    @Test
    void routesPlayerQueriesAndAssertionsToThePlayerService() throws Exception {
        String expression = "(the som-pace of *View)";
        when(kmEngine.evaluatePlayer("PlayerA", expression))
                .thenReturn(result(true, "(steady)", null, null, List.of()));

        for (String route : List.of("query", "assertions")) {
            mvc.perform(post("/km/players/PlayerA/" + route).contentType("application/json")
                            .content("{\"expression\":\"(the som-pace of *View)\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("(steady)"))
                    .andExpect(jsonPath("$.nextSession").doesNotExist());
        }
    }

    @Test
    void returnsPlayerErrorsUsingTheExistingHttpContract() throws Exception {
        when(kmEngine.evaluatePlayer("Global", "test"))
                .thenThrow(new IllegalArgumentException("Player cannot be Global"));
        when(kmEngine.evaluatePlayer("PlayerA", "test"))
                .thenReturn(result(true, "nil", null, null, List.of("Reasoning failed")));

        mvc.perform(post("/km/players/Global/query").contentType("application/json")
                        .content("{\"expression\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Player cannot be Global"));
        mvc.perform(post("/km/players/PlayerA/assertions").contentType("application/json")
                        .content("{\"expression\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Reasoning failed"));
    }

    @Test
    void queriesTheEngineWithoutExposingSessionState() throws Exception {
        when(kmEngine.evaluate("(the color of Fred)"))
                .thenReturn(result(true, "(red)", null, null, List.of()));

        mvc.perform(post("/km/query").contentType("application/json")
                        .content("{\"expression\":\"(the color of Fred)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("(red)"))
                .andExpect(jsonPath("$.nextSession").doesNotExist());
        verify(kmEngine).evaluate("(the color of Fred)");
    }

    @Test
    void assertsThroughTheSameEngine() throws Exception {
        String expression = "(Fred has (color (red)))";
        when(kmEngine.evaluate(expression)).thenReturn(result(true, "(Fred)", null, null, List.of()));

        mvc.perform(post("/km/assertions").contentType("application/json")
                        .content("{\"expression\":\"(Fred has (color (red)))\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        verify(kmEngine).evaluate(expression);
    }

    @Test
    void returnsValidationErrorsInTheStandardShape() throws Exception {
        when(kmEngine.evaluate(null)).thenThrow(new IllegalArgumentException("Expression cannot be null or empty"));

        mvc.perform(post("/km/query").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Expression cannot be null or empty"));
    }

    @Test
    void returnsMalformedJsonInTheStandardShape() throws Exception {
        mvc.perform(post("/km/assertions").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A JSON body with an expression is required"));
    }

    @Test
    void returnsReadAndReasoningErrorsAsBadRequests() throws Exception {
        when(kmEngine.evaluate("(a")).thenReturn(result(false, null, "read", "Incomplete term", List.of()));
        when(kmEngine.evaluate("invalid")).thenReturn(result(true, "nil", null, null, List.of("Invalid expression")));

        mvc.perform(post("/km/query").contentType("application/json").content("{\"expression\":\"(a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Incomplete term"));
        mvc.perform(post("/km/assertions").contentType("application/json")
                        .content("{\"expression\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid expression"));
    }

    @Test
    void returnsUnsupportedExpressionsAsNotImplemented() throws Exception {
        when(kmEngine.evaluate("unsupported"))
                .thenReturn(result(false, null, "not-ported", "Command is not ported", List.of()));

        mvc.perform(post("/km/query").contentType("application/json")
                        .content("{\"expression\":\"unsupported\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("Command is not ported"));
    }

    @Test
    void keepsUnexpectedFailureDetailsOutOfTheResponse() throws Exception {
        when(kmEngine.evaluate("(1 + 2)"))
                .thenThrow(new KmEngineException(new IllegalStateException("private details")));

        mvc.perform(post("/km/query").contentType("application/json")
                        .content("{\"expression\":\"(1 + 2)\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Knowledge base evaluation failed"));
    }

    @Test
    void answersAQuestionFromTheLoadedLibrary() throws Exception {
        when(kmEngine.evaluateBaseline(List.of("(*Goal has (instance-of (Som-Level-Goal)))", "(the som-notch of *View)")))
                .thenReturn(List.of(result(true, "(*Goal)", null, null, List.of()),
                        result(true, "(steady)", null, null, List.of())));

        mvc.perform(post("/km/baseline").contentType("application/json")
                        .content("{\"expressions\":[\"(*Goal has (instance-of (Som-Level-Goal)))\",\"(the som-notch of *View)\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.results[1].answer").value("(steady)"))
                .andExpect(jsonPath("$.results[0].nextSession").doesNotExist());
        verify(kmEngine, never()).evaluate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAnEmptyBaselineQuestion() throws Exception {
        when(kmEngine.evaluateBaseline(anyList()))
                .thenThrow(new IllegalArgumentException("Expressions cannot be null or empty"));

        mvc.perform(post("/km/baseline").contentType("application/json").content("{\"expressions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Expressions cannot be null or empty"));
    }

    @Test
    void returnsABaselineReasoningErrorWithoutKeepingIt() throws Exception {
        when(kmEngine.evaluateBaseline(List.of("(bad)")))
                .thenReturn(List.of(result(true, "nil", null, null, List.of("Invalid expression"))));

        mvc.perform(post("/km/baseline").contentType("application/json")
                        .content("{\"expressions\":[\"(bad)\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid expression"))
                .andExpect(jsonPath("$.results").doesNotExist());
    }

    private static KmResult result(boolean ok, String answer, String error, String message, List<String> errors) {
        return new KmResult(ok, null, answer, error, message, false, errors, List.of(), List.of(), null, null);
    }
}
