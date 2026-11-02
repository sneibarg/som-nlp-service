package org.springy.som.nlp.judge;

import org.springy.som.nlp.judge.internal.JudgeConflictException;
import org.springy.som.nlp.judge.internal.JudgeNotFoundException;
import org.springy.som.nlp.judge.internal.JudgeRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lists, creates, and deletes request types and their verdicts in the judges collection.
 */
@Service
public class JudgeCatalog {

    static final String REQUEST_NAME_REQUIRED = "Request type name cannot be null or empty";
    static final String VERDICT_NAME_REQUIRED = "Verdict name cannot be null or empty";

    private final JudgeRepository repository;

    public JudgeCatalog(JudgeRepository repository) {
        this.repository = repository;
    }

    /**
     * @return every request type, ordered by name
     */
    public List<RequestType> listRequests() {
        List<RequestType> requests = new ArrayList<>(repository.findAll());
        requests.sort(Comparator.comparing(RequestType::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        return requests;
    }

    /**
     * @return every verdict, with the request type it belongs to
     */
    public List<VerdictListing> listVerdicts() {
        List<VerdictListing> listings = new ArrayList<>();
        for (RequestType request : listRequests()) {
            for (VerdictType verdict : verdictsOf(request)) {
                if (verdict == null) {
                    continue;
                }
                listings.add(new VerdictListing(request.getName(), verdict.getName(), verdict.getMeaning()));
            }
        }
        return listings;
    }

    /**
     * @param requestName the request type name
     * @return the verdicts defined for that request type
     */
    public List<VerdictType> listVerdicts(String requestName) {
        return new ArrayList<>(verdictsOf(requireRequest(requestName)));
    }

    /**
     * Stores a request type. A client-sent id is ignored. The name is required and must be unique.
     * @param request the request type to store
     * @return the stored request type
     */
    public RequestType createRequest(RequestType request) {
        if (request == null) {
            throw new IllegalArgumentException(REQUEST_NAME_REQUIRED);
        }
        String name = requiredName(request.getName(), REQUEST_NAME_REQUIRED);
        if (repository.existsByName(name)) {
            throw new JudgeConflictException(duplicateRequest(name));
        }
        request.setId(null);
        request.setName(name);
        request.setVerdicts(normalizeVerdicts(request.getVerdicts()));
        try {
            return repository.save(request);
        }
        catch (DuplicateKeyException ex) {
            throw new JudgeConflictException(duplicateRequest(name));
        }
    }

    /**
     * @param name the request type name
     */
    public void deleteRequest(String name) {
        String trimmed = requiredName(name, REQUEST_NAME_REQUIRED);
        if (repository.findByName(trimmed).isEmpty()) {
            throw new JudgeNotFoundException(missingRequest(trimmed));
        }
        repository.deleteByName(trimmed);
    }

    /**
     * Adds a verdict to an existing request type. The verdict name must be unique within that type.
     * @param requestName the request type name
     * @param verdict the verdict to add
     * @return the stored verdict
     */
    public VerdictType createVerdict(String requestName, VerdictType verdict) {
        String verdictName = requiredName(verdict == null ? null : verdict.getName(), VERDICT_NAME_REQUIRED);
        RequestType request = requireRequest(requestName);
        List<VerdictType> verdicts = new ArrayList<>(verdictsOf(request));
        if (verdicts.stream().anyMatch(existing -> verdictName.equals(existing == null ? null : existing.getName()))) {
            throw new JudgeConflictException(duplicateVerdict(verdictName));
        }
        VerdictType stored = new VerdictType();
        stored.setName(verdictName);
        stored.setMeaning(verdict.getMeaning());
        verdicts.add(stored);
        request.setVerdicts(verdicts);
        repository.save(request);
        return stored;
    }

    /**
     * @param requestName the request type name
     * @param verdictName the verdict name
     */
    public void deleteVerdict(String requestName, String verdictName) {
        String trimmedVerdict = requiredName(verdictName, VERDICT_NAME_REQUIRED);
        RequestType request = requireRequest(requestName);
        List<VerdictType> verdicts = new ArrayList<>(verdictsOf(request));
        boolean removed = verdicts.removeIf(existing -> existing != null && trimmedVerdict.equals(existing.getName()));
        if (!removed) {
            throw new JudgeNotFoundException(missingVerdict(trimmedVerdict));
        }
        request.setVerdicts(verdicts);
        repository.save(request);
    }

    private RequestType requireRequest(String name) {
        String trimmed = requiredName(name, REQUEST_NAME_REQUIRED);
        return repository.findByName(trimmed)
                .orElseThrow(() -> new JudgeNotFoundException(missingRequest(trimmed)));
    }

    private static List<VerdictType> normalizeVerdicts(List<VerdictType> verdicts) {
        List<VerdictType> normalized = new ArrayList<>();
        if (verdicts == null) {
            return normalized;
        }
        Set<String> names = new HashSet<>();
        for (VerdictType verdict : verdicts) {
            String name = requiredName(verdict == null ? null : verdict.getName(), VERDICT_NAME_REQUIRED);
            if (!names.add(name)) {
                throw new JudgeConflictException(duplicateVerdict(name));
            }
            VerdictType stored = new VerdictType();
            stored.setName(name);
            stored.setMeaning(verdict.getMeaning());
            normalized.add(stored);
        }
        return normalized;
    }

    private static List<VerdictType> verdictsOf(RequestType request) {
        return request.getVerdicts() == null ? List.of() : request.getVerdicts();
    }

    private static String requiredName(String name, String message) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return name.trim();
    }

    private static String duplicateRequest(String name) {
        return "A request type named " + name + " already exists";
    }

    private static String duplicateVerdict(String name) {
        return "A verdict named " + name + " already exists";
    }

    private static String missingRequest(String name) {
        return "Request type " + name + " was not found";
    }

    private static String missingVerdict(String name) {
        return "Verdict " + name + " was not found";
    }
}
