package org.springy.som.nlp.km.internal;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    void writesCorrelatedSingleLineExpressionsAndOutcomesToTheDedicatedLog() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger("km.expressions");
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            bridge.evaluate(knowledgeBase.startupSession(), "(1 +\n 2)");
            bridge.evaluateInSituation(knowledgeBase.startupSession(), "*Global", "(a");
            assertThrows(RuntimeException.class, () ->
                    bridge.evaluateInSituation(knowledgeBase.startupSession(), "[", "(1 + 2)"));

            var messages = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertEquals(6, messages.size());
            assertTrue(messages.get(0).contains("expression=(1 +\\n 2)"));
            assertTrue(messages.get(1).contains("success=true"));
            assertTrue(messages.get(2).contains("situation=*Global expression=(a"));
            assertTrue(messages.get(3).contains("success=false error=read"));
            assertTrue(messages.get(5).contains("event=failed"));
            for (int index = 0; index < messages.size(); index += 2) {
                String id = messages.get(index).split("id=")[1].split(" ")[0];
                assertTrue(messages.get(index + 1).contains("id=" + id));
            }
            assertTrue(messages.stream().noneMatch(message -> message.contains("\n") || message.contains("\r")));
            assertFalse(logger.isAdditive());
            var fileAppender = (RollingFileAppender<?>) logger.getAppender("KM_EXPRESSIONS");
            assertTrue(fileAppender.isStarted());
            assertTrue(Files.readString(Path.of(fileAppender.getFile())).contains(messages.get(0)));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void playerEndpointsKeepFactsAndInferencesInTheirOwnSituations() throws Exception {
        var service = new KmEngineService(bridge, knowledgeBase);
        var mvc = MockMvcBuilders.standaloneSetup(new KmController(service)).build();

        for (String player : List.of("PlayerA", "PlayerB")) {
            String value = player.equals("PlayerA") ? "red" : "blue";
            mvc.perform(post("/km/players/" + player + "/assertions").contentType("application/json")
                            .content("{\"expression\":\"(*View has (player-value (" + value + ")))\"}"))
                    .andExpect(status().isOk());
        }
        for (String player : List.of("PlayerA", "PlayerB")) {
            String value = player.equals("PlayerA") ? "red" : "blue";
            mvc.perform(post("/km/players/" + player + "/query").contentType("application/json")
                            .content("{\"expression\":\"(the player-value of *View)\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("(" + value + ")"));
        }
        assertEquals("(red)", service.evaluatePlayer("*PlayerA", "(the player-value of *View)").answer());
        assertEquals("nil", service.evaluate("(the player-value of *View)").answer());
        assertEquals("(*Global)", service.evaluate("(curr-situation)").answer());
        assertEquals("nil", new KmEngineService(bridge, knowledgeBase)
                .evaluatePlayer("PlayerA", "(the player-value of *View)").answer());
    }

    @Test
    void playerFailureDiscardsPartialFactsAndCreation() {
        var service = new KmEngineService(bridge, knowledgeBase);
        assertTrue(service.evaluatePlayer("PlayerA", "(*View has (player-value (red)))").errors().isEmpty());

        assertFalse(service.evaluatePlayer("PlayerA", "(a").ok());
        assertFalse(service.evaluatePlayer("NewPlayer", "(a").ok());
        assertFalse(service.evaluate("(the instance-of of *NewPlayer)").answer().contains("Som-Agentic-Player"));
        assertEquals("(red)", service.evaluatePlayer("PlayerA", "(the player-value of *View) ; trailing comment").answer());
        assertFalse(service.evaluatePlayer("PlayerA", "(the player-value of *View)) (global-situation)").ok());
        assertEquals("(*Global)", service.evaluate("(curr-situation)").answer());
    }

    @Test
    void playerQueriesDeriveValuesFromGlobalRulesInTheirOwnSituation() {
        var service = new KmEngineService(bridge, knowledgeBase);
        for (String player : List.of("PlayerA", "PlayerB")) {
            String value = player.equals("PlayerA") ? "red" : "blue";
            var stored = service.evaluatePlayer(player,
                    "(*DerivedView has (instance-of (PlayerView)) (player-value (" + value + ")))" );
            assertTrue(stored.ok());
            assertTrue(stored.errors().isEmpty(), stored.errors()::toString);
            var derived = service.evaluatePlayer(player, "(the player-derived of *DerivedView)");
            assertTrue(derived.errors().isEmpty(), derived.errors()::toString);
            assertEquals("(" + value + ")", derived.answer());
        }
        assertEquals("(red)", service.evaluatePlayer("PlayerA", "(the player-derived of *DerivedView)").answer());
        assertEquals("nil", service.evaluate("(the player-derived of *DerivedView)").answer());
    }

    @Test
    void configuredStartupUsesTheBundledSelectionWithoutRewritingPaths() {
        var libraries = bridge.configuredLibraries();

        assertEquals(List.of("src/resources/km/core-library", "src/resources/km/specs", "src/resources/km/som-library"),
                libraries.stream().map(KmEngineBridge.Library::directory).toList());
        assertEquals("(load-lib \"src/resources/km/core-library\" :silent t)", libraries.get(0).expression());
    }

    @Test
    void configuredStartupLoadsNestedClasspathComponentsAndExcludesOtherLibraries() {
        var service = new KmEngineService(bridge, knowledgeBase);

        assertEquals("(violet)", service.evaluate("(the library-color of NestedLibraryFixture)").answer());
        assertEquals("nil", service.evaluate("(the library-color of ExcludedLibraryFixture)").answer());
    }

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
        return new KmKnowledgeBase(bridge, file.toString());
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
