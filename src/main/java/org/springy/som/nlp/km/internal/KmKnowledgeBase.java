package org.springy.som.nlp.km.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
class KmKnowledgeBase {
    private final KmEngineBridge.Session startupSession;

    KmKnowledgeBase(KmEngineBridge bridge, @Value("${km.knowledge-base:}") String location) {
        var libraries = bridge.configuredLibraries();
        if (libraries.isEmpty()) {
            throw new IllegalStateException("KM configuration must select at least one component library");
        }
        var librariesLoaded = bridge.newSession();
        for (var library : libraries) {
            librariesLoaded = loadLibrary(bridge, librariesLoaded, library);
        }
        startupSession = location.isBlank() ? librariesLoaded : load(bridge, librariesLoaded, Path.of(location));
    }

    /**
     * Returns the session only after configured component libraries and any optional KB have loaded.
     */
    KmEngineBridge.Session startupSession() {
        return startupSession;
    }

    private static KmEngineBridge.Session loadLibrary(KmEngineBridge bridge, KmEngineBridge.Session session,
                                                       KmEngineBridge.Library library) {
        log.info("Loading {}", library.label());
        var result = bridge.evaluate(session, library.expression());
        // The configured specs directory intentionally has no .km files and answers nil.
        if (!result.ok() || !result.errors().isEmpty()) {
            String detail = result.errors().isEmpty() ? result.message() : String.join(", ", result.errors());
            throw new IllegalStateException("Could not load KM component library " + library.directory()
                    + (detail == null ? "" : ": " + detail));
        }
        log.info("Loaded {}", library.label());
        return result.nextSession();
    }

    private static KmEngineBridge.Session load(KmEngineBridge bridge, KmEngineBridge.Session initialSession,
                                                Path location) {
        Path file = location.toAbsolutePath();
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalStateException("KM knowledge base must be a readable file: " + file);
        }

        // The KM reader uses backslashes to escape the next character in a string.
        String path = quotePath(file);
        KmEngineBridge.Evaluation result = bridge.evaluate(initialSession, "(load-kb \"" + path + "\")");
        if (!result.ok() || !result.errors().isEmpty() || !"(t)".equals(result.answer())) {
            String detail = result.errors().isEmpty() ? result.message() : String.join(", ", result.errors());
            throw new IllegalStateException("Could not load KM knowledge base " + file
                    + (detail == null ? "" : ": " + detail));
        }
        log.info("Loaded KM knowledge base from {}", file);
        return result.nextSession();
    }

    private static String quotePath(Path path) {
        return path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
