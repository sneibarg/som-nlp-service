package org.springy.som.nlp.service;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springy.som.nlp.nlp.service.NlpService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NlpServiceTest {

    @Mock
    private StanfordCoreNLP pipeline;

    @InjectMocks
    private NlpService nlpService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        nlpService = new NlpService(pipeline);
    }

    @Test
    void testGetNamedEntities() {
        // Arrange
        String text = "Sophia works at Stanford.";
        Annotation document = new Annotation(text);
        CoreMap sentence = mock(CoreMap.class);
        CoreLabel token1 = mock(CoreLabel.class);
        CoreLabel token2 = mock(CoreLabel.class);
        when(token1.get(CoreAnnotations.TextAnnotation.class)).thenReturn("Sophia");
        when(token1.get(CoreAnnotations.NamedEntityTagAnnotation.class)).thenReturn("PERSON");
        when(token2.get(CoreAnnotations.TextAnnotation.class)).thenReturn("Stanford");
        when(token2.get(CoreAnnotations.NamedEntityTagAnnotation.class)).thenReturn("ORGANIZATION");
        when(sentence.get(CoreAnnotations.TokensAnnotation.class)).thenReturn(Arrays.asList(token1, token2));
        doAnswer(invocation -> {
            Annotation doc = invocation.getArgument(0);
            doc.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
            return null;
        }).when(pipeline).annotate(document);

        // Act
        List<Map<String, String>> result = nlpService.getNamedEntities(text);

        // Assert
        assertEquals(2, result.size());
        assertEquals("Sophia", result.get(0).get("entity"));
        assertEquals("PERSON", result.get(0).get("type"));
        assertEquals("Stanford", result.get(1).get("entity"));
        assertEquals("ORGANIZATION", result.get(1).get("type"));
    }

    @Test
    void testGetSentiment() {
        // Arrange
        String text = "I love this!";
        Annotation document = new Annotation(text);
        CoreMap sentence = mock(CoreMap.class);
        when(sentence.get(SentimentCoreAnnotations.SentimentClass.class)).thenReturn("Positive");
        when(sentence.toString()).thenReturn("I love this!");
        doAnswer(invocation -> {
            Annotation doc = invocation.getArgument(0);
            doc.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
            return null;
        }).when(pipeline).annotate(document);

        // Act
        List<Map<String, Object>> result = nlpService.getSentiment(text);

        // Assert
        assertEquals(1, result.size());
        assertEquals("I love this!", result.get(0).get("sentence"));
        assertEquals("Positive", result.get(0).get("sentiment"));
        assertEquals(0.5, (Double) result.get(0).get("score"), 0.001);
    }

    @Test
    void testGetCoreferences() {
        // Arrange
        String text = "Sophia is busy. She works.";
        Annotation document = new Annotation(text);
        CorefChain chain = mock(CorefChain.class);
        CorefChain.CorefMention representative = mock(CorefChain.CorefMention.class);
        CorefChain.CorefMention mention = mock(CorefChain.CorefMention.class);
        ReflectionTestUtils.setField(representative, "mentionSpan", "Sophia");
        ReflectionTestUtils.setField(mention, "mentionSpan", "She");
        when(chain.getRepresentativeMention()).thenReturn(representative);
        when(chain.getMentionsInTextualOrder()).thenReturn(Arrays.asList(representative, mention));
        Map<Integer, CorefChain> corefChains = new HashMap<>();
        corefChains.put(1, chain);
        doAnswer(invocation -> {
            Annotation doc = invocation.getArgument(0);
            doc.set(CorefCoreAnnotations.CorefChainAnnotation.class, corefChains);
            return null;
        }).when(pipeline).annotate(document);

        // Act
        Map<String, List<String>> result = nlpService.getCoreferences(text);

        // Assert
        assertEquals(1, result.size());
        assertTrue(result.containsKey("Sophia"));
        assertEquals(Arrays.asList("Sophia", "She"), result.get("Sophia"));
    }

    @Test
    void testTokenizeAndSplit() {
        // Arrange
        String text = "Sophia works. She rests.";
        Annotation document = new Annotation(text);
        CoreMap sentence1 = mock(CoreMap.class);
        CoreMap sentence2 = mock(CoreMap.class);
        CoreLabel token1 = mock(CoreLabel.class);
        CoreLabel token2 = mock(CoreLabel.class);
        CoreLabel token3 = mock(CoreLabel.class);
        CoreLabel token4 = mock(CoreLabel.class);
        when(token1.get(CoreAnnotations.TextAnnotation.class)).thenReturn("Sophia");
        when(token2.get(CoreAnnotations.TextAnnotation.class)).thenReturn("works");
        when(token3.get(CoreAnnotations.TextAnnotation.class)).thenReturn("She");
        when(token4.get(CoreAnnotations.TextAnnotation.class)).thenReturn("rests");
        when(sentence1.get(CoreAnnotations.TokensAnnotation.class)).thenReturn(Arrays.asList(token1, token2));
        when(sentence2.get(CoreAnnotations.TokensAnnotation.class)).thenReturn(Arrays.asList(token3, token4));
        doAnswer(invocation -> {
            Annotation doc = invocation.getArgument(0);
            doc.set(CoreAnnotations.SentencesAnnotation.class, Arrays.asList(sentence1, sentence2));
            return null;
        }).when(pipeline).annotate(document);

        // Act
        Map<String, List<List<String>>> result = nlpService.tokenizeAndSplit(text);

        // Assert
        assertEquals(2, result.get("sentences").size());
        assertEquals(Arrays.asList("Sophia", "works"), result.get("sentences").get(0));
        assertEquals(Arrays.asList("She", "rests"), result.get("sentences").get(1));
    }

    @Test
    void testGetDetailedDependencies() {
        // Arrange
        String text = "Sophia works.";
        Annotation document = new Annotation(text);
        CoreMap sentence = mock(CoreMap.class);
        SemanticGraph graph = mock(SemanticGraph.class);
        SemanticGraphEdge edge = mock(SemanticGraphEdge.class);
        IndexedWord governor = mock(IndexedWord.class);
        IndexedWord dependent = mock(IndexedWord.class);
        when(governor.word()).thenReturn("works");
        when(governor.index()).thenReturn(2);
        when(dependent.word()).thenReturn("Sophia");
        when(dependent.index()).thenReturn(1);
        when(edge.getRelation()).thenReturn(GrammaticalRelation.valueOf("nsubj"));
        when(edge.getGovernor()).thenReturn(governor);
        when(edge.getDependent()).thenReturn(dependent);
        when(graph.edgeListSorted()).thenReturn(Collections.singletonList(edge));
        when(sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)).thenReturn(graph);
        doAnswer(invocation -> {
            Annotation doc = invocation.getArgument(0);
            doc.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
            return null;
        }).when(pipeline).annotate(document);

        // Act
        List<List<Map<String, Object>>> result = nlpService.getDetailedDependencies(text);

        // Assert
        assertEquals(1, result.size());
        Map<String, Object> dep = result.get(0).get(0);
        assertEquals("nsubj", dep.get("relation"));
        assertEquals("works", dep.get("governor"));
        assertEquals(2, dep.get("governorIndex"));
        assertEquals("Sophia", dep.get("dependent"));
        assertEquals(1, dep.get("dependentIndex"));
    }

    @Test
    void testGetOpenIERelationsWithConfidence() {
        // Arrange
        String text = "Sophia manages the team.";
        Annotation document = new Annotation(text);
        CoreMap sentence = mock(CoreMap.class);
        edu.stanford.nlp.ie.util.RelationTriple triple = mock(edu.stanford.nlp.ie.util.RelationTriple.class);
        when(triple.subjectLemmaGloss()).thenReturn("Sophia");
        when(triple.relationLemmaGloss()).thenReturn("manage");
        when(triple.objectLemmaGloss()).thenReturn("team");
        ReflectionTestUtils.setField(triple, "confidence", 0.95);
//        when(triple.confidence).thenReturn(0.95);
        when(sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class)).thenReturn(Collections.singletonList(triple));
        doAnswer(invocation -> {
            Annotation doc = invocation.getArgument(0);
            doc.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
            return null;
        }).when(pipeline).annotate(document);

        // Act
        List<Map<String, Object>> result = nlpService.getOpenIERelationsWithConfidence(text);

        // Assert
        assertEquals(1, result.size());
        Map<String, Object> rel = result.get(0);
        assertEquals("Sophia", rel.get("subject"));
        assertEquals("manage", rel.get("predicate"));
        assertEquals("team", rel.get("object"));
        assertEquals(0.95, (Double) rel.get("confidence"), 0.001);
    }
}