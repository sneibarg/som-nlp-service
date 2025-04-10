package com.springmud.nlp.NlpService.config;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.jupiter.api.Test;
import org.som.nlp.NlpService.NlpServiceApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = NlpServiceApplication.class)
class NlpConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void testStanfordCoreNLPBeanCreation() {
        // Act
        StanfordCoreNLP stanfordCoreNLP = context.getBean(StanfordCoreNLP.class);

        // Assert
        assertNotNull(stanfordCoreNLP, "StanfordCoreNLP bean should be created");
    }
}