package org.som.nlp.NlpService.service;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
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

@Service
public class RelationService {
    private final StanfordCoreNLP pipeline;

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
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text cannot be null or empty");
        }

        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sentences = processSentences(document);
        result.put("sentences", sentences);
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
                List<Map<String, String>> tokens = new ArrayList<>();
                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    String word = token.get(CoreAnnotations.TextAnnotation.class);
                    String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                    tokens.add(Map.of("token", word, "pos", pos, "lemma", lemma));
                }
                sentenceData.put("tokens", tokens);

                List<Map<String, String>> entities = new ArrayList<>();
                for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    String word = token.get(CoreAnnotations.TextAnnotation.class);
                    String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                    if (!"O".equals(ne)) {
                        entities.add(Map.of("entity", word, "type", ne));
                    }
                }
                sentenceData.put("entities", entities);

                SemanticGraph depGraph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
                List<Map<String, String>> dependencies = new ArrayList<>();
                if (depGraph != null) { // Add null check
                    for (SemanticGraphEdge edge : depGraph.edgeListSorted()) {
                        String relation = edge.getRelation().toString();
                        String governor = edge.getGovernor().word();
                        String dependent = edge.getDependent().word();
                        dependencies.add(Map.of("relation", relation, "governor", governor, "dependent", dependent));
                    }
                }
                sentenceData.put("dependencies", dependencies);
                Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
                sentenceData.put("parseTree", tree.toString());
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