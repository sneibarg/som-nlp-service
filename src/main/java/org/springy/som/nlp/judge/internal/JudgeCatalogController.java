package org.springy.som.nlp.judge.internal;

import org.springy.som.nlp.judge.JudgeCatalog;
import org.springy.som.nlp.judge.RequestType;
import org.springy.som.nlp.judge.VerdictListing;
import org.springy.som.nlp.judge.VerdictType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Lists, creates, and deletes request types and the verdicts that belong to them.
 */
@RestController
@RequestMapping("/judge")
public class JudgeCatalogController {

    private final JudgeCatalog catalog;

    public JudgeCatalogController(JudgeCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/request-types")
    public List<RequestType> listRequests() {
        return catalog.listRequests();
    }

    @PostMapping("/request-types")
    public ResponseEntity<RequestType> createRequest(@RequestBody RequestType request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createRequest(request));
    }

    @DeleteMapping("/request-types/{name}")
    public ResponseEntity<Void> deleteRequest(@PathVariable String name) {
        catalog.deleteRequest(name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verdicts")
    public List<VerdictListing> listVerdicts() {
        return catalog.listVerdicts();
    }

    @GetMapping("/request-types/{name}/verdicts")
    public List<VerdictType> listVerdicts(@PathVariable String name) {
        return catalog.listVerdicts(name);
    }

    @PostMapping("/request-types/{name}/verdicts")
    public ResponseEntity<VerdictType> createVerdict(@PathVariable String name, @RequestBody VerdictType verdict) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createVerdict(name, verdict));
    }

    @DeleteMapping("/request-types/{name}/verdicts/{verdictName}")
    public ResponseEntity<Void> deleteVerdict(@PathVariable String name, @PathVariable String verdictName) {
        catalog.deleteVerdict(name, verdictName);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(JudgeNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(JudgeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(JudgeConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(JudgeConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
