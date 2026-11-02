package org.springy.som.nlp.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springy.som.nlp.judge.internal.JudgeConflictException;
import org.springy.som.nlp.judge.internal.JudgeNotFoundException;
import org.springy.som.nlp.judge.internal.JudgeRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeCatalogTest {

    @Mock
    private JudgeRepository repository;

    @InjectMocks
    private JudgeCatalog catalog;

    @Test
    void listsRequestTypesByName() {
        when(repository.findAll()).thenReturn(List.of(request("zeta"), request("alpha")));

        List<RequestType> requests = catalog.listRequests();

        assertEquals(List.of("alpha", "zeta"), requests.stream().map(RequestType::getName).toList());
    }

    @Test
    void listsVerdictsAcrossRequestTypes() {
        RequestType later = request("zeta");
        later.setVerdicts(List.of(verdict("no", "negative")));
        RequestType earlier = request("alpha");
        earlier.setVerdicts(List.of(verdict("yes", "positive")));
        when(repository.findAll()).thenReturn(List.of(later, earlier));

        List<VerdictListing> listings = catalog.listVerdicts();

        assertEquals(List.of(
                new VerdictListing("alpha", "yes", "positive"),
                new VerdictListing("zeta", "no", "negative")), listings);
    }

    @Test
    void createsARequestTypeAndIgnoresTheClientId() {
        when(repository.existsByName("tone")).thenReturn(false);
        when(repository.save(any(RequestType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RequestType request = new RequestType();
        request.setId("client-id");
        request.setName(" tone ");
        request.setSystemPrompt("Be brief.");
        request.setVerdicts(List.of(verdict(" good ", "acceptable")));

        RequestType stored = catalog.createRequest(request);

        assertNull(stored.getId());
        assertEquals("tone", stored.getName());
        assertEquals("Be brief.", stored.getSystemPrompt());
        assertEquals("good", stored.getVerdicts().get(0).getName());
        assertEquals("acceptable", stored.getVerdicts().get(0).getMeaning());
    }

    @Test
    void rejectsABlankRequestTypeName() {
        RequestType request = new RequestType();
        request.setName("  ");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> catalog.createRequest(request));

        assertEquals(JudgeCatalog.REQUEST_NAME_REQUIRED, failure.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsADuplicateRequestTypeName() {
        when(repository.existsByName("tone")).thenReturn(true);
        RequestType request = request("tone");

        JudgeConflictException failure = assertThrows(JudgeConflictException.class,
                () -> catalog.createRequest(request));

        assertEquals("A request type named tone already exists", failure.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void reportsADuplicateKeyAsAConflict() {
        when(repository.existsByName("tone")).thenReturn(false);
        when(repository.save(any(RequestType.class))).thenThrow(new DuplicateKeyException("E11000"));

        JudgeConflictException failure = assertThrows(JudgeConflictException.class,
                () -> catalog.createRequest(request("tone")));

        assertEquals("A request type named tone already exists", failure.getMessage());
    }

    @Test
    void rejectsDuplicateVerdictsSubmittedWithTheRequestType() {
        when(repository.existsByName("tone")).thenReturn(false);
        RequestType request = request("tone");
        request.setVerdicts(List.of(verdict("good", "one"), verdict(" good ", "two")));

        JudgeConflictException failure = assertThrows(JudgeConflictException.class,
                () -> catalog.createRequest(request));

        assertEquals("A verdict named good already exists", failure.getMessage());
    }

    @Test
    void deletesARequestTypeByName() {
        when(repository.findByName("tone")).thenReturn(Optional.of(request("tone")));

        catalog.deleteRequest(" tone ");

        verify(repository).deleteByName("tone");
    }

    @Test
    void reportsAMissingRequestType() {
        when(repository.findByName("tone")).thenReturn(Optional.empty());

        JudgeNotFoundException failure = assertThrows(JudgeNotFoundException.class,
                () -> catalog.deleteRequest("tone"));

        assertEquals("Request type tone was not found", failure.getMessage());
        verify(repository, never()).deleteByName("tone");
    }

    @Test
    void addsAVerdictToARequestType() {
        RequestType request = request("tone");
        request.setVerdicts(new ArrayList<>());
        when(repository.findByName("tone")).thenReturn(Optional.of(request));
        when(repository.save(any(RequestType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VerdictType stored = catalog.createVerdict(" tone ", verdict(" good ", "acceptable"));

        assertEquals("good", stored.getName());
        assertEquals("acceptable", stored.getMeaning());
        assertEquals(List.of("good"), request.getVerdicts().stream().map(VerdictType::getName).toList());
    }

    @Test
    void rejectsADuplicateVerdictName() {
        RequestType request = request("tone");
        request.setVerdicts(new ArrayList<>(List.of(verdict("good", "acceptable"))));
        when(repository.findByName("tone")).thenReturn(Optional.of(request));

        JudgeConflictException failure = assertThrows(JudgeConflictException.class,
                () -> catalog.createVerdict("tone", verdict("good", "again")));

        assertEquals("A verdict named good already exists", failure.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void deletesAVerdict() {
        RequestType request = request("tone");
        request.setVerdicts(new ArrayList<>(List.of(verdict("good", "acceptable"), verdict("poor", "not acceptable"))));
        when(repository.findByName("tone")).thenReturn(Optional.of(request));
        when(repository.save(any(RequestType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        catalog.deleteVerdict("tone", " good ");

        assertEquals(List.of("poor"), request.getVerdicts().stream().map(VerdictType::getName).toList());
    }

    @Test
    void reportsAMissingVerdict() {
        RequestType request = request("tone");
        request.setVerdicts(new ArrayList<>());
        when(repository.findByName("tone")).thenReturn(Optional.of(request));

        JudgeNotFoundException failure = assertThrows(JudgeNotFoundException.class,
                () -> catalog.deleteVerdict("tone", "good"));

        assertEquals("Verdict good was not found", failure.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void listsVerdictsForOneRequestType() {
        RequestType request = request("tone");
        request.setVerdicts(List.of(verdict("good", "acceptable")));
        when(repository.findByName("tone")).thenReturn(Optional.of(request));

        List<VerdictType> verdicts = catalog.listVerdicts("tone");

        assertEquals("good", verdicts.get(0).getName());
        verdicts.clear();
        assertEquals(1, request.getVerdicts().size());
    }

    @Test
    void reportsAMissingRequestTypeWhenListingItsVerdicts() {
        when(repository.findByName("tone")).thenReturn(Optional.empty());

        JudgeNotFoundException failure = assertThrows(JudgeNotFoundException.class,
                () -> catalog.listVerdicts("tone"));

        assertEquals("Request type tone was not found", failure.getMessage());
    }

    private static RequestType request(String name) {
        RequestType request = new RequestType();
        request.setName(name);
        return request;
    }

    private static VerdictType verdict(String name, String meaning) {
        VerdictType verdict = new VerdictType();
        verdict.setName(name);
        verdict.setMeaning(meaning);
        return verdict;
    }
}
