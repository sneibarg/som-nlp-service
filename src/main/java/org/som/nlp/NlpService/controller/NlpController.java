package org.som.nlp.NlpService.controller;

import lombok.extern.slf4j.Slf4j;
import org.som.nlp.NlpService.service.NlpService;
import org.som.nlp.NlpService.service.RelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/nlp")
@Slf4j
public class NlpController {
    private final NlpService nlpService;
    private final RelationService relationService;

    @Autowired
    public NlpController(NlpService nlpService, RelationService relationService) {
        this.nlpService = nlpService;
        this.relationService = relationService;
    }

    /**
     * Endpoint to extract all relations from the input text using Stanford CoreNLP.
     * @param text The text to analyze.
     * @return A ResponseEntity containing a map of all extracted annotations.
     */
    @PostMapping("/relations")
    public ResponseEntity<Map<String, Object>> getAllRelations(@RequestBody String text) {
        try {
            Map<String, Object> relations = relationService.getAllRelations(text);
            return ResponseEntity.ok(relations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ner")
    public ResponseEntity<List<Map<String, String>>> getNamedEntities(@RequestBody String text) {
        return ResponseEntity.ok(nlpService.getNamedEntities(text));
    }

    @PostMapping("/sentiment")
    public ResponseEntity<List<Map<String, Object>>> getSentiment(@RequestBody String text) {
        return ResponseEntity.ok(nlpService.getSentiment(text));
    }

    @PostMapping("/coref")
    public ResponseEntity<Map<String, List<String>>> getCoreferences(@RequestBody String text) {
        return ResponseEntity.ok(nlpService.getCoreferences(text));
    }

    @PostMapping("/tokenize")
    public ResponseEntity<Map<String, List<List<String>>>> tokenizeAndSplit(@RequestBody String text) {
        return ResponseEntity.ok(nlpService.tokenizeAndSplit(text));
    }
}
