package org.springy.som.nlp.config;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.jupiter.api.Test;
import org.springy.som.nlp.NlpServiceApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = NlpServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NlpConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void testStanfordCoreNLPBeanCreation() {
        StanfordCoreNLP stanfordCoreNLP = context.getBean(StanfordCoreNLP.class);

        assertNotNull(stanfordCoreNLP, "StanfordCoreNLP bean should be created");
    }
}