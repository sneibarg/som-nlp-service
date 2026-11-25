package org.springy.som.nlp.km.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApplicationModuleTest
class KmEngineBridgeTests {

    @Autowired
    private KmEngineBridge bridge;

    @Autowired
    private KmKnowledgeBase knowledgeBase;

    @Autowired
    private KmController controller;

    @Test
    void endpointsAssertAndQueryThroughTheInitializedEngine() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/km/query").contentType("application/json")
                        .content("{\"expression\":\"(the library-color of (the library-parent of SomLibraryFixture))\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("(green)"));
        mvc.perform(post("/km/assertions").contentType("application/json")
                        .content("{\"expression\":\"(HttpFixture has (color (yellow)))\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/km/query").contentType("application/json")
                        .content("{\"expression\":\"(the color of HttpFixture)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("(yellow)"));
    }

    @Test
    void serviceQueriesStartupFactsAndRetainsAssertions() {
        var service = new KmEngineService(bridge, knowledgeBase);

        assertEquals("(blue)", service.evaluate("(the color of StartupFixture)").answer());
        assertTrue(service.evaluate("(RestFixture has (color (red)))").errors().isEmpty());
        assertEquals("(red)", service.evaluate("(the color of RestFixture)").answer());
        assertEquals("nil", bridge.evaluate(knowledgeBase.startupSession(), "(the color of RestFixture)").answer());
    }

    @Test
    void serviceKeepsAssertionsAfterAnUnreadableExpression() {
        var service = new KmEngineService(bridge, knowledgeBase);
        service.evaluate("(RestFixture has (color (red)))");

        assertFalse(service.evaluate("(a").ok());
        assertEquals("(red)", service.evaluate("(the color of RestFixture)").answer());
    }

    @Test
    void serviceRejectsBlankExpressions() {
        var service = new KmEngineService(bridge, knowledgeBase);

        for (String expression : new String[]{null, "", "  "}) {
            var failure = assertThrows(IllegalArgumentException.class, () -> service.evaluate(expression));
            assertEquals("Expression cannot be null or empty", failure.getMessage());
        }
    }

    @Test
    void loadsConfiguredFactsAtStartup() {
        String query = "(the color of StartupFixture)";

        assertEquals("(blue)", bridge.evaluate(knowledgeBase.startupSession(), query).answer());
        assertEquals("nil", bridge.evaluate(bridge.newSession(), query).answer());
    }

    @Test
    void rejectsAMissingKnowledgeBase(@TempDir Path directory) {
        Path file = directory.resolve("missing.km");

        var failure = assertThrows(IllegalStateException.class, () -> startupWithFile(file));

        assertTrue(failure.getMessage().contains(file.toString()));
    }

    @Test
    void rejectsAPartiallyLoadedKnowledgeBase(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("invalid.km"), "(Fred has (color (red)))\n(a");

        var failure = assertThrows(IllegalStateException.class, () -> startupWithFile(file));

        assertTrue(failure.getMessage().startsWith("Could not load KM knowledge base"));
    }

    @Test
    void callsThePackagedEngine() {
        assertEquals("2.5.45", bridge.status().kmVersion());
        assertEquals("interpreter", bridge.status().evaluator());

        var result = bridge.evaluate(bridge.newSession(), "(1 + 2)");

        assertTrue(result.ok());
        assertEquals("(3)", result.answer());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void apiSessionContainsBothComponentLibrariesAndTheConfiguredFacts() {
        var service = new KmEngineService(bridge, knowledgeBase);

        assertEquals("(green)", service.evaluate("(the library-color of CoreLibraryFixture)").answer());
        assertEquals("(green)", service.evaluate(
                "(the library-color of (the library-parent of SomLibraryFixture))").answer());
        assertEquals("(blue)", service.evaluate("(the color of StartupFixture)").answer());
    }

    private KmKnowledgeBase startupWithFile(Path file) {
        return new KmKnowledgeBase(bridge, "src/test/resources/km/core-library",
                "src/test/resources/km/som-library", file.toString());
    }

    @Test
    void carriesStateAcrossCallsAndKeepsSessionsIndependent() {
        var initial = bridge.newSession();
        var stored = bridge.evaluate(initial, "(Fred has (color (red)))");

        assertTrue(stored.ok());
        assertEquals("(red)", bridge.evaluate(stored.nextSession(), "(the color of Fred)").answer());
        assertEquals("nil", bridge.evaluate(initial, "(the color of Fred)").answer());
        assertEquals("nil", bridge.evaluate(bridge.newSession(), "(the color of Fred)").answer());
    }

    @Test
    void keepsThePreviousSessionWhenTheReaderCannotProduceATerm() {
        var session = bridge.newSession();
        var unreadable = bridge.evaluate(session, "(a");

        assertFalse(unreadable.ok());
        assertEquals("read", unreadable.error());
        assertTrue(unreadable.incomplete());
        assertNull(unreadable.answer());
        assertSame(session, unreadable.nextSession());
    }

    @Test
    void savesAndLoadsWithTheExistingKmCommands(@TempDir Path directory) {
        var stored = bridge.evaluate(bridge.newSession(), "(Fred has (color (red)))");
        Path file = directory.resolve("knowledge-base.km");
        // KM treats backslashes as reader escapes, including in Windows paths.
        String path = file.toString().replace('\\', '/');

        var saved = bridge.evaluate(stored.nextSession(), "(save-kb \"" + path + "\")");
        assertTrue(saved.errors().isEmpty(), saved.errors()::toString);
        assertTrue(Files.isRegularFile(file));

        var loaded = bridge.evaluate(bridge.newSession(), "(load-kb \"" + path + "\")");
        assertTrue(loaded.errors().isEmpty(), loaded.errors()::toString);
        assertEquals("(red)", bridge.evaluate(loaded.nextSession(), "(the color of Fred)").answer());
    }
}
