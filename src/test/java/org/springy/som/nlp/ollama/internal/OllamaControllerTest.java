package org.springy.som.nlp.ollama.internal;

import org.junit.jupiter.api.Test;
import org.springy.som.nlp.ollama.OllamaClient;
import org.springy.som.nlp.ollama.OllamaException;
import org.springy.som.nlp.ollama.OllamaRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OllamaController.class)
class OllamaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OllamaClient client;

    @Test
    void returnsTheCompletion() throws Exception {
        when(client.complete(any(OllamaRequest.class))).thenReturn("ok");

        mockMvc.perform(post("/ollama/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":"Hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("ok"));
    }

    @Test
    void sendsASchemaObjectToTheClient() throws Exception {
        when(client.complete(any(OllamaRequest.class))).thenReturn("{\"label\":\"good\"}");

        mockMvc.perform(post("/ollama/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "llama3.2",
                                  "system": "Be brief.",
                                  "user": "Grade this.",
                                  "jsonSchema": {
                                    "type": "object",
                                    "properties": {"label": {"type": "string"}},
                                    "required": ["label"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("{\"label\":\"good\"}"));

        org.mockito.ArgumentCaptor<OllamaRequest> captor = org.mockito.ArgumentCaptor.forClass(OllamaRequest.class);
        verify(client).complete(captor.capture());
        OllamaRequest request = captor.getValue();
        assertEquals("llama3.2", request.model());
        assertEquals("Be brief.", request.system());
        assertEquals("Grade this.", request.user());
        assertTrue(request.jsonSchema().contains("\"label\""));
    }

    @Test
    void sendsASchemaStringToTheClient() throws Exception {
        when(client.complete(any(OllamaRequest.class))).thenReturn("ok");

        mockMvc.perform(post("/ollama/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":"Hello","jsonSchema":"{\\"type\\":\\"object\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("ok"));

        org.mockito.ArgumentCaptor<OllamaRequest> captor = org.mockito.ArgumentCaptor.forClass(OllamaRequest.class);
        verify(client).complete(captor.capture());
        assertEquals("{\"type\":\"object\"}", captor.getValue().jsonSchema());
    }

    @Test
    void rejectsABlankUserPrompt() throws Exception {
        when(client.complete(any(OllamaRequest.class)))
                .thenThrow(new IllegalArgumentException("User prompt cannot be null or empty"));

        mockMvc.perform(post("/ollama/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User prompt cannot be null or empty"));
    }

    @Test
    void reportsAnUpstreamFailure() throws Exception {
        when(client.complete(any(OllamaRequest.class)))
                .thenThrow(new OllamaException("Ollama could not complete the request"));

        mockMvc.perform(post("/ollama/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":"Hello"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Ollama could not complete the request"));
    }
}
