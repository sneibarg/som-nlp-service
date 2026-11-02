# NlpService

Spring Boot NLP service. Base package `org.springy.som.nlp`. Java 17, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring AI 2.0.1. The app listens on port 9081.

## Skills

Follow the skill. Do not copy its steps into this file.

- **spring-modulith** (`.grok/skills/spring-modulith/SKILL.md`, `/spring-modulith`). Use it when structuring modules, fixing a boundary violation, or writing a Modulith test. It owns feature choice, allowed dependencies, and the verification checklist. This repo imports `spring-modulith-bom` and uses `spring-modulith-starter-core` plus test-scoped `spring-modulith-starter-test`. Leave versions off those artifacts. The event publication registry, `spring-modulith-starter-mongodb`, insight, Documenter, Moments, nested modules, and `Type.OPEN` stay out until that skill selects them and the user asks.
- **java-code-reviewer** (`.grok/skills/java-code-reviewer/SKILL.md`, `/java-code-reviewer`). Use it when reviewing Java. It writes a findings report and leaves the code unchanged. Change the code only when the user asks for the change. Judge new code against the Coding Standards below and against the Java release in `pom.xml`.

## Architecture

Direct subpackages of `org.springy.som.nlp` are the modules. `NlpServiceApplication` is the only type in the base package. No module declares `@ApplicationModule`. The modules do not depend on each other.

| Module | Public types | HTTP |
| --- | --- | --- |
| `corenlp` | `NlpService`, `RelationService` | `POST /nlp/...` |
| `wordnet` | `WordNetParser`, `SynsetDTO` | `GET /wordnet/synsets` |
| `judge` | `JudgeService`, `JudgeCatalog`, `JudgeRequest`, `JudgeVerdict`, `RequestType`, `VerdictType`, `ScoreRange`, `VerdictListing` | `POST /judge`, `/judge/request-types`, `/judge/verdicts` |
| `ollama` | `OllamaClient`, `OllamaRequest`, `OllamaCompletion`, `OllamaException` | `POST /ollama/completions` |

Controllers, repositories, configuration, and module-specific exceptions live in `internal`. Another module may use only the public types.

`JudgeService.judge` validates the text and claim, then throws `JudgeUnavailableException`. It does not read MongoDB and it does not call Ollama or SpaceXAI. Request types are documents in the `judges` collection. `src/main/resources/mongo/NLP/judges.json` is the import file. `JudgeCatalog` is the catalog API. `XaiVerdictModel` is the SpaceXAI client and stays unwired from `JudgeService`. `OllamaClient` serves any prompt, including a future judge request.

Stanford annotators are `tokenize,ssplit,pos,lemma,ner,parse,depparse,natlog,coref,sentiment,openie`. WordNet uses extjwnl. The GraphQL starter is on the classpath and has no schema.

## Coding Standards

- Match the indentation of the file you edit. Most of `src/main/java` uses 4 spaces. `NlpServiceApplication` and several tests use tabs. Leave a file's indentation as it is.
- Use explicit imports. One constructor, assigned to `final` fields. Skip `@Autowired` on new constructors.
- Put a type other modules may call in the module base package. Put controllers, repositories, configuration, and exceptions in `internal`. Package-private members of the base package are invisible to `internal`.
- Use a `record` for a request or response that is not a MongoDB document. Use Lombok `@Data` for a document (`RequestType`, `VerdictType`, `ScoreRange`) and for `SynsetDTO`.
- Reject a blank required string with `IllegalArgumentException` and a message that names the value, such as `Text cannot be null or empty`. Wrap an upstream failure in a specific runtime exception, log the cause, and keep a stable message for the caller.
- Return HTTP errors as `{"error":"<message>"}`. `IllegalArgumentException` is 400. A missing judge resource is 404. A duplicate name is 409. An upstream model failure is 502. `JudgeUnavailableException` is 503. Handle those on the controller that owns the route. `POST /nlp/openie-relations` already returns a list on failure; leave that response as it is.
- Spring Boot 4 uses Jackson 3 (`tools.jackson.databind`). On a `JsonNode`, call `isString()` and `asString()`.
- Tests are package-private. Use JUnit 5. `@WebMvcTest` is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`. `@MockitoBean` is `org.springframework.test.context.bean.override.mockito.MockitoBean`. A controller test mocks the service. A client test mocks the chat model. A module with a Spring bean has an `@ApplicationModuleTest` in that module. Leave `ModularityTests` and `NlpServiceApplicationTests` in place. `verifyAutomatically` stays at its default.
- Add test properties in `src/test/resources/application.properties`. That file adds to `src/main/resources/application.yml`. A file named `application.yml` under `src/test/resources` replaces the main file. Keep the Mongo auto-configurations excluded there, and `@MockitoBean` `JudgeRepository` on any context that would query it. Tests do not connect to `mongodb://dragon:27017`, `localhost:11434`, or `https://api.x.ai`.
- Leave versions off artifacts managed by the Spring Boot parent, `spring-ai-bom`, or `spring-modulith-bom`. Keep the explicit versions on Stanford CoreNLP, extjwnl, `javax.xml.bind:jaxb-api` 2.3.1, and `org.glassfish.jaxb:jaxb-runtime` 2.3.9. WordNet needs the javax JAXB artifacts. Leave `jakarta.xml.bind-api` 4.0.5 as the separate Jakarta API.
- Leave Mongo auto-index-creation off. `JudgeCatalog` enforces unique request-type and verdict names.
- Read `XAI_API_KEY` from the environment. The property `${XAI_API_KEY:_}` means the key is unset.
- Comment a constraint the next reader cannot see from the code. Javadoc on a public method matches the file you are editing.

## Build and test

On Windows PowerShell, set `JAVA_HOME` for the command. The JDK is `C:\Program Files\Java\jdk-17.0.5`. The `java` executable on `PATH` is a shim, so it is an invalid `JAVA_HOME`. Chain commands with `;`.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
.\mvnw.cmd test
```

Surefire uses `-Xshare:off`. Leave that `argLine` in place. `target/` is gitignored, including `target/generated-spring-modulith/`.

## Configuration

`src/main/resources/application.yml` is the runtime config.

- Leave `spring.ai.model.chat` unset so the SpaceXAI and Ollama chat models both start. One value, such as `openai`, switches every other chat model off.
- Leave `spring.ai.chat.client.enabled: false`. Two `ChatModel` beans are present, and the shared `ChatClient` builder accepts one.
- Leave `spring.ai.ollama.init.pull-model-strategy: never`. Change `spring.ai.ollama.base-url` and `spring.ai.ollama.chat.model` only when the user changes the local Ollama service.
- SpaceXAI uses `spring.ai.openai.base-url: https://api.x.ai/v1` and `spring.ai.openai.chat.model: grok-4.7`.
- MongoDB is `spring.mongodb.uri: mongodb://dragon:27017/NLP`. Import `judges.json` only when the user asks.
