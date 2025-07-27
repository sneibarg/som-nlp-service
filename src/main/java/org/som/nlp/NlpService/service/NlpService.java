package org.som.nlp.NlpService.service;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NlpService {
    private final StanfordCoreNLP pipeline;

    @Autowired
    public NlpService(StanfordCoreNLP pipeline) {
        this.pipeline = pipeline;
    }

    public List<Map<String, String>> getNamedEntities(String text) {
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<Map<String, String>> entities = new ArrayList<>();
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                if (!"O".equals(ne)) {
                    entities.add(Map.of("entity", word, "type", ne));
                }
            }
        }
        return entities;
    }

    public List<Map<String, Object>> getSentiment(String text) {
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<Map<String, Object>> sentiments = new ArrayList<>();
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            double score = sentiment.equals("Positive") ? 0.5 : sentiment.equals("Negative") ? -0.5 : 0.0;
            sentiments.add(Map.of("sentence", sentence.toString(), "sentiment", sentiment, "score", score));
        }
        return sentiments;
    }

    public Map<String, List<String>> getCoreferences(String text) {
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        Map<String, List<String>> coreferences = new HashMap<>();
        if (corefChains != null) {
            for (Map.Entry<Integer, CorefChain> entry : corefChains.entrySet()) {
                CorefChain chain = entry.getValue();
                String representative = chain.getRepresentativeMention().mentionSpan;
                List<String> mentions = chain.getMentionsInTextualOrder().stream()
                        .map(m -> m.mentionSpan)
                        .collect(Collectors.toList());
                coreferences.put(representative, mentions);
            }
        }
        return coreferences;
    }

    public Map<String, List<List<String>>> tokenizeAndSplit(String text) {
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<List<String>> sentences = new ArrayList<>();
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            List<String> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class)
                    .stream()
                    .map(token -> token.get(CoreAnnotations.TextAnnotation.class))
                    .collect(Collectors.toList());
            sentences.add(tokens);
        }
        return Map.of("sentences", sentences);
    }

    public Map<String, List<CoreSentence>> split(String text) {
        CoreDocument document = new CoreDocument(text);
        pipeline.annotate(document);
        List<CoreSentence> sentences = document.sentences();
        return Map.of("sentences", sentences);
    }

    public List<List<Map<String, Object>>> getDetailedDependencies(String text) {
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<List<Map<String, Object>>> sentenceDependencies = new ArrayList<>();
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph dependencyGraph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
            List<Map<String, Object>> dependencies = new ArrayList<>();
            if (dependencyGraph != null) { // Add null check
                for (SemanticGraphEdge edge : dependencyGraph.edgeListSorted()) {
                    String relation = edge.getRelation().toString();
                    String governor = edge.getGovernor().word();
                    int governorIndex = edge.getGovernor().index();
                    String dependent = edge.getDependent().word();
                    int dependentIndex = edge.getDependent().index();
                    dependencies.add(Map.of(
                            "relation", relation,
                            "governor", governor,
                            "governorIndex", governorIndex,
                            "dependent", dependent,
                            "dependentIndex", dependentIndex
                    ));
                }
            }
            sentenceDependencies.add(dependencies);
        }
        return sentenceDependencies;
    }

    public List<Map<String, Object>> getOpenIERelationsWithConfidence(String text) {
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<Map<String, Object>> triples = new ArrayList<>();
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            for (edu.stanford.nlp.ie.util.RelationTriple triple : sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class)) {
                String subject = triple.subjectLemmaGloss();
                String predicate = triple.relationLemmaGloss();
                String object = triple.objectLemmaGloss();
                double confidence = triple.confidence;
                triples.add(Map.of(
                        "subject", subject,
                        "predicate", predicate,
                        "object", object,
                        "confidence", confidence
                ));
            }
        }
        return triples;
    }
}