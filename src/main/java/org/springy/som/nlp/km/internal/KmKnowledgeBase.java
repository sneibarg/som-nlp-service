package org.springy.som.nlp.km.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
class KmKnowledgeBase {
    private final KmEngineBridge.Session startupSession;

    KmKnowledgeBase(KmEngineBridge bridge,
                    @Value("${km.component-library}") String componentLibrary,
                    @Value("${km.som-component-library}") String somComponentLibrary,
                    @Value("${km.knowledge-base:}") String location) {
        var coreLoaded = loadLibrary(bridge, bridge.newSession(), componentLibrary, "KM component library");
        var librariesLoaded = loadLibrary(bridge, coreLoaded, somComponentLibrary, "SOM component library");
        startupSession = location.isBlank() ? librariesLoaded : load(bridge, librariesLoaded, Path.of(location));
    }

    /**
     * Returns the session only after both component libraries and any configured KB have loaded.
     */
    KmEngineBridge.Session startupSession() {
        return startupSession;
    }

    private static KmEngineBridge.Session loadLibrary(KmEngineBridge bridge, KmEngineBridge.Session session,
                                                       String location, String name) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException(name + " directory is required");
        }
        Path directory = Path.of(location).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            throw new IllegalStateException(name + " must be a readable directory: " + directory);
        }
        try (var files = Files.walk(directory)) {
            if (files.noneMatch(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".km"))) {
                throw new IllegalStateException(name + " contains no KM components: " + directory);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + name + ": " + directory, ex);
        }
        var result = bridge.loadLibrary(session, directory.toString());
        if (!result.ok() || !result.errors().isEmpty() || result.answer() == null || "nil".equals(result.answer())) {
            String detail = result.errors().isEmpty() ? result.message() : String.join(", ", result.errors());
            throw new IllegalStateException("Could not load " + name + " " + directory
                    + (detail == null ? "" : ": " + detail));
        }
        log.info("Loaded {} from {}", name, directory);
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
