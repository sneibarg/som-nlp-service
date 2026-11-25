package org.springy.som.nlp.km.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            HttpStatus status = "not-ported".equals(result.error())
                    ? HttpStatus.NOT_IMPLEMENTED : HttpStatus.BAD_REQUEST;
            String message = result.message() != null ? result.message()
                    : result.errors().isEmpty() ? "KM expression could not be evaluated"
                    : String.join("; ", result.errors());
            return ResponseEntity.status(status).body(Map.of("error", message));
        }
        return ResponseEntity.ok(result);
    }
}
