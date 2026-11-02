package org.springy.som.nlp.judge.internal;

import org.junit.jupiter.api.Test;
import org.springy.som.nlp.judge.JudgeCatalog;
import org.springy.som.nlp.judge.RequestType;
import org.springy.som.nlp.judge.VerdictListing;
import org.springy.som.nlp.judge.VerdictType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JudgeCatalogController.class)
class JudgeCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JudgeCatalog catalog;

    @Test
    void listsRequestTypes() throws Exception {
        RequestType request = new RequestType();
        request.setName("claim-support");
        VerdictType supported = new VerdictType();
        supported.setName("supported");
        supported.setMeaning("the text states the claim or directly entails it");
        request.setVerdicts(List.of(supported));
        when(catalog.listRequests()).thenReturn(List.of(request));

        mockMvc.perform(get("/judge/request-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("claim-support"))
                .andExpect(jsonPath("$[0].verdicts[0].name").value("supported"))
                .andExpect(jsonPath("$[0].verdicts[0].meaning").value("the text states the claim or directly entails it"));
    }

    @Test
    void createsARequestType() throws Exception {
        RequestType stored = new RequestType();
        stored.setName("tone");
        when(catalog.createRequest(any(RequestType.class))).thenReturn(stored);

        mockMvc.perform(post("/judge/request-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"tone","systemPrompt":"Be brief."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("tone"));
    }

    @Test
    void rejectsABlankRequestTypeName() throws Exception {
        when(catalog.createRequest(any(RequestType.class)))
                .thenThrow(new IllegalArgumentException("Request type name cannot be null or empty"));

        mockMvc.perform(post("/judge/request-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request type name cannot be null or empty"));
    }

    @Test
    void rejectsADuplicateRequestType() throws Exception {
        when(catalog.createRequest(any(RequestType.class)))
                .thenThrow(new JudgeConflictException("A request type named tone already exists"));

        mockMvc.perform(post("/judge/request-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"tone"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A request type named tone already exists"));
    }

    @Test
    void deletesARequestType() throws Exception {
        mockMvc.perform(delete("/judge/request-types/tone"))
                .andExpect(status().isNoContent());
    }

    @Test
    void reportsAMissingRequestType() throws Exception {
        doThrow(new JudgeNotFoundException("Request type tone was not found")).when(catalog).deleteRequest("tone");

        mockMvc.perform(delete("/judge/request-types/tone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Request type tone was not found"));
    }

    @Test
    void listsVerdicts() throws Exception {
        when(catalog.listVerdicts()).thenReturn(List.of(
                new VerdictListing("claim-support", "supported", "the text states the claim or directly entails it")));

        mockMvc.perform(get("/judge/verdicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].request").value("claim-support"))
                .andExpect(jsonPath("$[0].name").value("supported"))
                .andExpect(jsonPath("$[0].meaning").value("the text states the claim or directly entails it"));
    }

    @Test
    void listsVerdictsForOneRequestType() throws Exception {
        VerdictType supported = new VerdictType();
        supported.setName("supported");
        supported.setMeaning("the text states the claim or directly entails it");
        when(catalog.listVerdicts("claim-support")).thenReturn(List.of(supported));

        mockMvc.perform(get("/judge/request-types/claim-support/verdicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("supported"));
    }

    @Test
    void createsAVerdict() throws Exception {
        VerdictType stored = new VerdictType();
        stored.setName("good");
        stored.setMeaning("acceptable");
        when(catalog.createVerdict(eq("tone"), any(VerdictType.class))).thenReturn(stored);

        mockMvc.perform(post("/judge/request-types/tone/verdicts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"good","meaning":"acceptable"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("good"))
                .andExpect(jsonPath("$.meaning").value("acceptable"));
    }

    @Test
    void rejectsADuplicateVerdict() throws Exception {
        when(catalog.createVerdict(eq("tone"), any(VerdictType.class)))
                .thenThrow(new JudgeConflictException("A verdict named good already exists"));

        mockMvc.perform(post("/judge/request-types/tone/verdicts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"good"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A verdict named good already exists"));
    }

    @Test
    void deletesAVerdict() throws Exception {
        mockMvc.perform(delete("/judge/request-types/tone/verdicts/good"))
                .andExpect(status().isNoContent());
    }

    @Test
    void reportsAMissingVerdict() throws Exception {
        doThrow(new JudgeNotFoundException("Verdict good was not found"))
                .when(catalog).deleteVerdict("tone", "good");

        mockMvc.perform(delete("/judge/request-types/tone/verdicts/good"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Verdict good was not found"));
    }
}
