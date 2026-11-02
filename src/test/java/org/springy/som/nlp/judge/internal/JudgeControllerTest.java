package org.springy.som.nlp.judge.internal;

import org.junit.jupiter.api.Test;
import org.springy.som.nlp.judge.JudgeService;
import org.springy.som.nlp.judge.JudgeVerdict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JudgeController.class)
class JudgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JudgeService judgeService;

    @Test
    void returnsTheVerdict() throws Exception {
        when(judgeService.judge("Sophia works at Stanford.", "Sophia works at Stanford."))
            .thenReturn(new JudgeVerdict("supported", 0.9, "The text states it."));

        mockMvc.perform(post("/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text":"Sophia works at Stanford.","claim":"Sophia works at Stanford."}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("supported"))
            .andExpect(jsonPath("$.score").value(0.9))
            .andExpect(jsonPath("$.rationale").value("The text states it."));
    }

    @Test
    void returnsBadRequestForAnEmptyText() throws Exception {
        when(judgeService.judge("", "A claim.")).thenThrow(new IllegalArgumentException("Text cannot be null or empty"));

        mockMvc.perform(post("/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text":"","claim":"A claim."}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Text cannot be null or empty"));
    }

    @Test
    void returnsServiceUnavailableWhenTheKeyIsMissing() throws Exception {
        when(judgeService.judge("A text.", "A claim."))
            .thenThrow(new JudgeUnavailableException("Set XAI_API_KEY to call the LLM judge"));

        mockMvc.perform(post("/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text":"A text.","claim":"A claim."}
                        """))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Set XAI_API_KEY to call the LLM judge"));
    }

    @Test
    void returnsBadGatewayWhenTheModelFails() throws Exception {
        when(judgeService.judge("A text.", "A claim."))
            .thenThrow(new JudgeUpstreamException("The LLM judge could not complete the request"));

        mockMvc.perform(post("/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"text":"A text.","claim":"A claim."}
                        """))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("The LLM judge could not complete the request"));
    }
}
