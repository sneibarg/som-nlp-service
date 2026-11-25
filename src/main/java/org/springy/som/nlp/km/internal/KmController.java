package org.springy.som.nlp.km.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/km")
public class KmController {

    private final KmEngineService kmEngine;

    public KmController(KmEngineService kmEngine) {
        this.kmEngine = kmEngine;
    }

    /**
     * Returns the answer to a KM query, retaining successful inference state.
     */
    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody KmExpression body) {
        return evaluate(body);
    }

    /**
     * Returns the result of asserting a KM expression into the current knowledge base.
     */
    @PostMapping("/assertions")
    public ResponseEntity<?> assertExpression(@RequestBody KmExpression body) {
        return evaluate(body);
    }

    /**
     * Queries in the given agentic player's situation, retaining successful inference state.
     */
    @PostMapping("/players/{player}/query")
    public ResponseEntity<?> queryPlayer(@PathVariable String player, @RequestBody KmExpression body) {
        return playerResult(player, body);
    }

    /**
     * Asserts an expression in the given agentic player's situation.
     */
    @PostMapping("/players/{player}/assertions")
    public ResponseEntity<?> assertPlayer(@PathVariable String player, @RequestBody KmExpression body) {
        return playerResult(player, body);
    }

    private ResponseEntity<?> playerResult(String player, KmExpression body) {
        KmResult result = kmEngine.evaluatePlayer(player, body == null ? null : body.expression());
        return !result.ok() || !result.errors().isEmpty() ? rejected(result) : ResponseEntity.ok(result);
    }

    /**
     * Answers one question from the loaded library. The derived frames are not kept.
     */
    @PostMapping("/baseline")
    public ResponseEntity<?> baseline(@RequestBody KmExpressions body) {
        List<KmResult> results = kmEngine.evaluateBaseline(body == null ? null : body.expressions());
        KmResult last = results.get(results.size() - 1);
        if (!last.ok() || !last.errors().isEmpty()) {
            return rejected(last);
        }
        return ResponseEntity.ok(new KmBaseline(true, results));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableBody() {
        return ResponseEntity.badRequest().body(Map.of("error", "A JSON body with an expression is required"));
    }

    @ExceptionHandler(KmEngineException.class)
    public ResponseEntity<Map<String, String>> engineFailure(KmEngineException ex) {
        return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
    }

    private ResponseEntity<?> evaluate(KmExpression body) {
        KmResult result = kmEngine.evaluate(body == null ? null : body.expression());
        if (!result.ok() || !result.errors().isEmpty()) {
            return rejected(result);
        }
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<?> rejected(KmResult result) {
        HttpStatus status = "not-ported".equals(result.error())
                ? HttpStatus.NOT_IMPLEMENTED : HttpStatus.BAD_REQUEST;
        String message = result.message() != null ? result.message()
                : result.errors().isEmpty() ? "KM expression could not be evaluated"
                : String.join("; ", result.errors());
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
