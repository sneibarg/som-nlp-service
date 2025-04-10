package com.springmud.nlp.NlpService.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.som.nlp.NlpService.service.RelationService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelationServiceTest {

    @Mock
    private StanfordCoreNLP pipeline;

    @InjectMocks
    private RelationService relationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        relationService = new RelationService(pipeline);
    }

    @Test
    void testGetAllRelations() {
        // Arrange
        String text = "Sophia might be cautious. She works hard.";
        Annotation document = new Annotation(text);

        // Mock first sentence
        CoreMap sentence1 = mock(CoreMap.class);
        CoreLabel token1 = mock(CoreLabel.class);
        CoreLabel token2 = mock(CoreLabel.class);
        when(token1.get(CoreAnnotations.TextAnnotation.class)).thenReturn("Sophia");
        when(token1.get(CoreAnnotations.PartOfSpeechAnnotation.class)).thenReturn("NNP");
        when(token1.get(CoreAnnotations.LemmaAnnotation.class)).thenReturn("Sophia");
        when(token1.get(CoreAnnotations.NamedEntityTagAnnotation.class)).thenReturn("PERSON");
        when(token2.get(CoreAnnotations.TextAnnotation.class)).thenReturn("cautious");
        when(token2.get(CoreAnnotations.PartOfSpeechAnnotation.class)).thenReturn("JJ");
        when(token2.get(CoreAnnotations.LemmaAnnotation.class)).thenReturn("cautious");
        when(token2.get(CoreAnnotations.NamedEntityTagAnnotation.class)).thenReturn("O");
        when(sentence1.get(CoreAnnotations.TokensAnnotation.class)).thenReturn(Arrays.asList(token1, token2));
        SemanticGraph graph1 = mock(SemanticGraph.class);
        when(graph1.edgeListSorted()).thenReturn(Collections.emptyList());
        when(sentence1.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)).thenReturn(graph1);
        Tree tree1 = mock(Tree.class);
        when(tree1.toString()).thenReturn("(S (NP Sophia) (VP might be cautious))");
        when(sentence1.get(TreeCoreAnnotations.TreeAnnotation.class)).thenReturn(tree1);
        when(sentence1.get(SentimentCoreAnnotations.SentimentClass.class)).thenReturn("Neutral");

        // Mock second sentence
        CoreMap sentence2 = mock(CoreMap.class);
        CoreLabel token3 = mock(CoreLabel.class);
        when(token3.get(CoreAnnotations.TextAnnotation.class)).thenReturn("She");
        when(token3.get(CoreAnnotations.PartOfSpeechAnnotation.class)).thenReturn("PRP");
        when(token3.get(CoreAnnotations.LemmaAnnotation.class)).thenReturn("she");
        when(token3.get(CoreAnnotations.NamedEntityTagAnnotation.class)).thenReturn("O");
        when(sentence2.get(CoreAnnotations.TokensAnnotation.class)).thenReturn(Collections.singletonList(token3));
        SemanticGraph graph2 = mock(SemanticGraph.class);
        when(graph2.edgeListSorted()).thenReturn(Collections.emptyList());
        when(sentence2.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)).thenReturn(graph2);
        Tree tree2 = mock(Tree.class);
        when(tree2.toString()).thenReturn("(S (NP She) (VP works hard))");
        when(sentence2.get(TreeCoreAnnotations.TreeAnnotation.class)).thenReturn(tree2);
        when(sentence2.get(SentimentCoreAnnotations.SentimentClass.class)).thenReturn("Positive");

        // Mock coreferences
        CorefChain chain = mock(CorefChain.class);
        CorefChain.CorefMention representative = mock(CorefChain.CorefMention.class);
        CorefChain.CorefMention mention = mock(CorefChain.CorefMention.class);
        ReflectionTestUtils.setField(representative, "mentionSpan", "Sophia");
        ReflectionTestUtils.setField(mention, "mentionSpan", "She");
//        when(representative.mentionSpan()).thenReturn("Sophia");
//        when(mention.mentionSpan()).thenReturn("She");
        when(chain.getRepresentativeMention()).thenReturn(representative);
        when(chain.getMentionsInTextualOrder()).thenReturn(Arrays.asList(representative, mention));
        Map<Integer, CorefChain> corefChains = new HashMap<>();
        corefChains.put(1, chain);

        doAnswer(invocation -> {
            Annotation doc = invocation.getArgument(0);
            doc.set(CoreAnnotations.SentencesAnnotation.class, Arrays.asList(sentence1, sentence2));
            doc.set(CorefCoreAnnotations.CorefChainAnnotation.class, corefChains);
            return null;
        }).when(pipeline).annotate(document);

        // Act
        Map<String, Object> result = relationService.getAllRelations(text);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("sentences"));
        assertTrue(result.containsKey("coreferences"));

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) result.get("sentences");
        assertEquals(2, sentences.size());

        Map<String, Object> firstSentence = sentences.get(0);
        List<Map<String, String>> tokens1 = (List<Map<String, String>>) firstSentence.get("tokens");
        assertEquals("Sophia", tokens1.get(0).get("token"));
        assertEquals("PERSON", ((List<Map<String, String>>) firstSentence.get("entities")).get(0).get("type"));
        assertEquals("Neutral", firstSentence.get("sentiment"));

        Map<String, List<String>> corefs = (Map<String, List<String>>) result.get("coreferences");
        assertEquals(Arrays.asList("Sophia", "She"), corefs.get("Sophia"));
    }

    @Test
    void testGetAllRelationsWithEmptyText() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> relationService.getAllRelations(""));
    }
}