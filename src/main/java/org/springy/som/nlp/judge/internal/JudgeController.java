package org.springy.som.nlp.judge.internal;

import org.springy.som.nlp.judge.JudgeRequest;
import org.springy.som.nlp.judge.JudgeService;
import org.springy.som.nlp.judge.JudgeVerdict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/judge")
public class JudgeController {

    private final JudgeService judgeService;

    public JudgeController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    /**
     * Judges whether the claim is supported by the text.
     * @param request the text and the claim
     * @return the verdict, or an error body
     */
    @PostMapping
    public ResponseEntity<?> judge(@RequestBody JudgeRequest request) {
        try {
            JudgeVerdict verdict = judgeService.judge(request.text(), request.claim());
            return ResponseEntity.ok(verdict);
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
        catch (JudgeUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
        }
        catch (JudgeUpstreamException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
        }
    }
}
