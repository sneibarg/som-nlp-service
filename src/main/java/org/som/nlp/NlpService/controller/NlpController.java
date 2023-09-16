package org.som.nlp.NlpService.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("nlp-service")
@Slf4j
public class NlpController {
    @GetMapping("/split-paragraph-to-sentences")
    public ResponseEntity<String> splitSentences() {
        StringBuilder responseBody = new StringBuilder();
        log.debug("Generic response.");
        responseBody.append("Generic response.");
        return new ResponseEntity<>(responseBody.toString(), HttpStatus.OK);
    }
}
