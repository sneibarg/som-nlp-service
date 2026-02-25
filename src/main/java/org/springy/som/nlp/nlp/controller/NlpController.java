package org.springy.som.nlp.nlp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springy.som.nlp.nlp.service.NlpService;
import org.springy.som.nlp.nlp.service.RelationService;
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

    @PostMapping("/openie-relations")
    public ResponseEntity<List<Map<String, Object>>> getOpenIERelations(@RequestBody String text) {
        try {
            List<Map<String, Object>> openIeRelations = nlpService.getOpenIERelationsWithConfidence(text);
            return ResponseEntity.ok(openIeRelations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(List.of(Map.of("error", e.getMessage())));
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

    @PostMapping("/parse")
    public ResponseEntity<List<Map<String, String>>> getParseTrees(@RequestBody String text) {
        return ResponseEntity.ok(nlpService.getParseTrees(text));
    }
}
