package com.springmud.nlp.NlpService.controller;


import org.junit.jupiter.api.Test;
import org.som.nlp.NlpService.NlpServiceApplication;
import org.som.nlp.NlpService.controller.NlpController;
import org.som.nlp.NlpService.service.NlpService;
import org.som.nlp.NlpService.service.RelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.*;

import static org.mockito.Mockito.*;

@WebFluxTest(NlpController.class)
@ContextConfiguration(classes = NlpServiceApplication.class)
class NlpControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private NlpService nlpService;

    @MockBean
    private RelationService relationService; // don't optimize out

    @Test
    void testGetNamedEntities() {
        // Arrange
        String text = "Sophia works at Stanford.";
        List<Map<String, String>> entities = Arrays.asList(
                Map.of("entity", "Sophia", "type", "PERSON"),
                Map.of("entity", "Stanford", "type", "ORGANIZATION")
        );
        when(nlpService.getNamedEntities(text)).thenReturn(entities);

        // Act & Assert
        webTestClient.post()
                .uri("/nlp/ner")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(text)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .hasSize(2)
                .contains(Map.of("entity", "Sophia", "type", "PERSON"), Map.of("entity", "Stanford", "type", "ORGANIZATION"));
    }

    @Test
    void testGetSentiment() {
        // Arrange
        String text = "I love this!";
        List<Map<String, Object>> sentiments = Collections.singletonList(
                Map.of("sentence", "I love this!", "sentiment", "Positive", "score", 0.5)
        );
        when(nlpService.getSentiment(text)).thenReturn(sentiments);

        // Act & Assert
        webTestClient.post()
                .uri("/nlp/sentiment")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(text)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .hasSize(1)
                .contains(Map.of("sentence", "I love this!", "sentiment", "Positive", "score", 0.5));
    }

    @Test
    void testGetCoreferences() {
        // Arrange
        String text = "Sophia is busy. She works.";
        Map<String, List<String>> coreferences = Collections.singletonMap("Sophia", Arrays.asList("Sophia", "She"));
        when(nlpService.getCoreferences(text)).thenReturn(coreferences);

        // Act & Assert
        webTestClient.post()
                .uri("/nlp/coref")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(text)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .isEqualTo(coreferences);
    }

    @Test
    void testTokenizeAndSplit() {
        // Arrange
        String text = "Sophia works. She rests.";
        Map<String, List<List<String>>> tokenized = Map.of(
                "sentences", Arrays.asList(
                        Arrays.asList("Sophia", "works"),
                        Arrays.asList("She", "rests")
                )
        );
        when(nlpService.tokenizeAndSplit(text)).thenReturn(tokenized);

        // Act & Assert
        webTestClient.post()
                .uri("/nlp/tokenize")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(text)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .isEqualTo(tokenized);
    }
}