package org.springy.som.nlp.controller;


import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springy.som.nlp.nlp.controller.NlpController;
import org.springy.som.nlp.nlp.service.NlpService;
import org.springy.som.nlp.nlp.service.RelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NlpController.class)
public class NlpControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NlpService nlpService;

    @MockitoBean
    private RelationService relationService; // don't optimize out

    @Test
    void testGetNamedEntities() throws Exception {
        String text = "Sophia works at Stanford.";
        List<Map<String, String>> entities = Arrays.asList(
                Map.of("entity", "Sophia", "type", "PERSON"),
                Map.of("entity", "Stanford", "type", "ORGANIZATION"));
        when(nlpService.getNamedEntities(text)).thenReturn(entities);

        mockMvc.perform(post("/nlp/ner")
                .contentType(MediaType.TEXT_PLAIN)
                .content(text))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].entity").value("Sophia"))
                .andExpect(jsonPath("$[0].type").value("PERSON"))
                .andExpect(jsonPath("$[1].entity").value("Stanford"))
                .andExpect(jsonPath("$[1].type").value("ORGANIZATION"));
    }

    @Test
    void testGetSentiment() throws Exception {
        String text = "I love this!";
        List<Map<String, Object>> sentiments = Collections.singletonList(
                Map.of("sentence", "I love this!", "sentiment", "Positive", "score", 0.5)
        );
        when(nlpService.getSentiment(text)).thenReturn(sentiments);

        mockMvc.perform(post("/nlp/sentiment")
                .contentType(MediaType.TEXT_PLAIN)
                .content(text))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sentence").value("I love this!"))
                .andExpect(jsonPath("$[0].sentiment").value("Positive"))
                .andExpect(jsonPath("$[0].score").value(0.5));
    }

    @Test
    void testGetCoreferences() throws Exception {
        String text = "Sophia is busy. She works.";
        Map<String, List<String>> coreferences = Collections.singletonMap("Sophia", Arrays.asList("Sophia", "She"));
        when(nlpService.getCoreferences(text)).thenReturn(coreferences);

        mockMvc.perform(post("/nlp/coref")
                .contentType(MediaType.TEXT_PLAIN)
                .content(text))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Sophia.length()").value(2))
                .andExpect(jsonPath("$.Sophia[0]").value("Sophia"))
                .andExpect(jsonPath("$.Sophia[1]").value("She"));
    }

    @Test
    void testTokenizeAndSplit() throws Exception {
        String text = "Sophia works. She rests.";
        Map<String, List<List<String>>> tokenized = Map.of(
                "sentences", Arrays.asList(
                        Arrays.asList("Sophia", "works"),
                        Arrays.asList("She", "rests")));
        when(nlpService.tokenizeAndSplit(text)).thenReturn(tokenized);

        mockMvc.perform(post("/nlp/tokenize")
                .contentType(MediaType.TEXT_PLAIN)
                .content(text))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentences.length()").value(2))
                .andExpect(jsonPath("$.sentences[0].length()").value(2))
                .andExpect(jsonPath("$.sentences[0][0]").value("Sophia"))
                .andExpect(jsonPath("$.sentences[0][1]").value("works"))
                .andExpect(jsonPath("$.sentences[1].length()").value(2))
                .andExpect(jsonPath("$.sentences[1][0]").value("She"))
                .andExpect(jsonPath("$.sentences[1][1]").value("rests"));
    }
}