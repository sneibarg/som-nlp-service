package org.springy.som.nlp.km.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    private static KmResult result(boolean ok, String answer, String error, String message, List<String> errors) {
        return new KmResult(ok, null, answer, error, message, false, errors, List.of(), List.of(), null, null);
    }
}
