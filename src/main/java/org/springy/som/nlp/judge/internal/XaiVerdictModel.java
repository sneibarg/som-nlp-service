package org.springy.som.nlp.judge.internal;

import lombok.extern.slf4j.Slf4j;
import org.springy.som.nlp.judge.JudgeVerdict;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class XaiVerdictModel implements VerdictModel {

    /**
     * Placeholder used when {@code XAI_API_KEY} is unset. The OpenAI client can be built with it,
     * and this component refuses to call the API until a real key replaces it.
     */
    static final String UNCONFIGURED_KEY = "_";

    static final String DEFAULT_MODEL = "grok-4.7";

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "verdict": {
                  "type": "string",
                  "enum": ["supported", "contradicted", "uncertain"]
                },
                "score": { "type": "number" },
                "rationale": { "type": "string" }
              },
              "required": ["verdict", "score", "rationale"],
              "additionalProperties": false
            }
            """;

    private final OpenAiChatModel chatModel;

    private final String apiKey;

    private final String model;

    private final BeanOutputConverter<JudgeVerdict> converter = new BeanOutputConverter<>(JudgeVerdict.class);

    public XaiVerdictModel(OpenAiChatModel chatModel,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.model:" + DEFAULT_MODEL + "}") String model) {
        this.chatModel = chatModel;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public JudgeVerdict judge(String system, String user) {
        if (apiKey == null || apiKey.isBlank() || UNCONFIGURED_KEY.equals(apiKey)) {
            throw new JudgeUnavailableException("Set XAI_API_KEY to call the LLM judge");
        }

        Prompt prompt = new Prompt(List.of(new SystemMessage(system), new UserMessage(user)),
                OpenAiChatOptions.builder()
                    .model(model)
                    .responseFormat(OpenAiChatModel.ResponseFormat.builder().jsonSchema(SCHEMA).strict(true).build())
                    .build());
        try {
            ChatResponse response = chatModel.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new JudgeUpstreamException("The LLM judge returned an empty response");
            }
            JudgeVerdict verdict = converter.convert(response.getResult().getOutput().getText());
            if (verdict == null) {
                throw new JudgeUpstreamException("The LLM judge returned an empty response");
            }
            return verdict;
        }
        catch (JudgeUpstreamException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            log.warn("LLM judge call failed: {}", ex.getMessage());
            throw new JudgeUpstreamException("The LLM judge could not complete the request");
        }
    }
}
