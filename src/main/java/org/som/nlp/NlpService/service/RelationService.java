package org.som.nlp.NlpService.service;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that extracts all available linguistic annotations from text using the Stanford CoreNLP library.
 */
@Service
public class RelationService {
    private final StanfordCoreNLP pipeline;

    /**
     * Constructs the RelationService with an injected StanfordCoreNLP pipeline.
     * @param pipeline The pre-configured StanfordCoreNLP pipeline.
     */
    @Autowired
    public RelationService(StanfordCoreNLP pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Processes the input text and returns a comprehensive map of all Stanford CoreNLP annotations.
     * @param text The text to analyze.
     * @return A map containing sentence-level and document-level annotations.
     */
    public Map<String, Object> getAllRelations(String text) {
        // Validate input
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text cannot be null or empty");
        }

        // Annotate the text
        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        // Initialize the result map
        Map<String, Object> result = new HashMap<>();

        // Extract sentence-level data
        List<Map<String, Object>> sentences = processSentences(document);
        result.put("sentences", sentences);

        // Extract document-level coreferences
        Map<String, List<String>> coreferences = processCoreferences(document);
        result.put("coreferences", coreferences);

        return result;
    }

    /**
     * Processes each sentence in the document to extract tokens, entities, dependencies, parse trees, and sentiment.
     * @param document The annotated document.
     * @return A list of maps, each representing a sentence's annotations.
     */
    private List<Map<String, Object>> processSentences(Annotation document) {
        List<CoreMap> sentenceList = document.get(CoreAnnotations.SentencesAnnotation.class);
        List<Map<String, Object>> sentences = new ArrayList<>();

        for (CoreMap sentence : sentenceList) {
            Map<String, Object> sentenceData = new HashMap<>();

            // Tokens with POS tags and lemmas
            List<Map<String, String>> tokens = new ArrayList<>();
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                tokens.add(Map.of(
                        "token", token.get(CoreAnnotations.TextAnnotation.class),
                        "pos", token.get(CoreAnnotations.PartOfSpeechAnnotation.class),
                        "lemma", token.get(CoreAnnotations.LemmaAnnotation.class)
                ));
            }
            sentenceData.put("tokens", tokens);

            // Named Entities
            List<Map<String, String>> entities = new ArrayList<>();
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                if (!"O".equals(ne)) {
                    entities.add(Map.of(
                            "entity", token.get(CoreAnnotations.TextAnnotation.class),
                            "type", ne
                    ));
                }
            }
            sentenceData.put("entities", entities);

            // Dependency Relations
            SemanticGraph depGraph = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
            List<Map<String, String>> dependencies = depGraph.edgeListSorted().stream()
                    .map(edge -> Map.of(
                            "relation", edge.getRelation().toString(),
                            "governor", edge.getGovernor().word(),
                            "dependent", edge.getDependent().word()
                    ))
                    .collect(Collectors.toList());
            sentenceData.put("dependencies", dependencies);

            // Constituency Parse Tree
            Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
            sentenceData.put("parseTree", tree.toString());

            // Sentiment
            String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            sentenceData.put("sentiment", sentiment != null ? sentiment : "Unknown");

            sentences.add(sentenceData);
        }
        return sentences;
    }

    /**
     * Extracts coreference chains from the document.
     * @param document The annotated document.
     * @return A map linking representative mentions to their coreferent mentions.
     */
    private Map<String, List<String>> processCoreferences(Annotation document) {
        Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        Map<String, List<String>> coreferences = new HashMap<>();
        if (corefChains != null) {
            for (CorefChain chain : corefChains.values()) {
                String representative = chain.getRepresentativeMention().mentionSpan;
                List<String> mentions = chain.getMentionsInTextualOrder().stream()
                        .map(m -> m.mentionSpan)
                        .collect(Collectors.toList());
                coreferences.put(representative, mentions);
            }
        }
        return coreferences;
    }
}