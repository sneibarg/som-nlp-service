package org.som.nlp.NlpService.config;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Configuration class for NLP components in the Spring application.
 */
@Configuration
public class NlpConfig {
    /**
     * Creates a StanfordCoreNLP pipeline bean with all required annotators.
     * @return A configured StanfordCoreNLP instance.
     * @throws RuntimeException if pipeline initialization fails.
     */
    @Bean
    public StanfordCoreNLP stanfordCoreNLP() {
        Properties props = new Properties();
        // Updated annotator list including 'natlog' for 'openie' support
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,natlog,coref,sentiment,openie");
        try {
            return new StanfordCoreNLP(props);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize StanfordCoreNLP pipeline: " + e.getMessage(), e);
        }
    }
}