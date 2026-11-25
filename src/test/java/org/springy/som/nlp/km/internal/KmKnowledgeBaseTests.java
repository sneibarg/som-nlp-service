package org.springy.som.nlp.km.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KmKnowledgeBaseTests {

    @Test
    void loadsBothLibrariesInOrderBeforePublishingTheSession(@TempDir Path directory) throws Exception {
        Path core = library(directory, "core");
        Path som = library(directory, "som");
        var bridge = mock(KmEngineBridge.class);
        var initial = mock(KmEngineBridge.Session.class);
        var coreLoaded = mock(KmEngineBridge.Session.class);
        var somLoaded = mock(KmEngineBridge.Session.class);
        when(bridge.newSession()).thenReturn(initial);
        when(bridge.loadLibrary(eq(initial), any())).thenReturn(loaded(coreLoaded));
        when(bridge.loadLibrary(eq(coreLoaded), any())).thenReturn(loaded(somLoaded));

        var knowledgeBase = new KmKnowledgeBase(bridge, core.toString(), som.toString(), "");

        assertSame(somLoaded, knowledgeBase.startupSession());
        var order = inOrder(bridge);
        order.verify(bridge).newSession();
        order.verify(bridge).loadLibrary(initial, core.toAbsolutePath().normalize().toString());
        order.verify(bridge).loadLibrary(coreLoaded, som.toAbsolutePath().normalize().toString());
    }

    @Test
    void stopsBeforeTheSecondLibraryIfTheFirstFails(@TempDir Path directory) throws Exception {
        Path core = library(directory, "core");
        Path som = library(directory, "som");
        var bridge = mock(KmEngineBridge.class);
        var initial = mock(KmEngineBridge.Session.class);
        when(bridge.newSession()).thenReturn(initial);
        when(bridge.loadLibrary(eq(initial), any())).thenReturn(failed(initial));

        var failure = assertThrows(IllegalStateException.class,
                () -> new KmKnowledgeBase(bridge, core.toString(), som.toString(), ""));

        assertTrue(failure.getMessage().contains("Could not load KM component library"));
        verify(bridge, never()).loadLibrary(any(), eq(som.toAbsolutePath().normalize().toString()));
    }

    @Test
    void failedSecondLoadPreventsTheApiBeansFromStarting(@TempDir Path directory) throws Exception {
        Path core = library(directory, "core");
        Path som = library(directory, "som");
        var bridge = mock(KmEngineBridge.class);
        var initial = mock(KmEngineBridge.Session.class);
        var coreLoaded = mock(KmEngineBridge.Session.class);
        when(bridge.newSession()).thenReturn(initial);
        when(bridge.loadLibrary(eq(initial), any())).thenReturn(loaded(coreLoaded));
        when(bridge.loadLibrary(eq(coreLoaded), any())).thenReturn(failed(coreLoaded));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(KmEngineBridge.class, () -> bridge);
            context.registerBean(KmKnowledgeBase.class,
                    () -> new KmKnowledgeBase(bridge, core.toString(), som.toString(), ""));
            context.registerBean("kmEngineService", KmEngineService.class);
            context.registerBean("kmController", KmController.class);

            var failure = assertThrows(BeanCreationException.class, context::refresh);

            assertTrue(failure.getMostSpecificCause().getMessage().contains("Could not load SOM component library"));
            assertFalse(context.getBeanFactory().containsSingleton("kmController"));
            assertFalse(context.getBeanFactory().containsSingleton("kmEngineService"));
        }
    }

    @Test
    void rejectsMissingEmptyAndBlankLibraryDirectories(@TempDir Path directory) throws Exception {
        var bridge = mock(KmEngineBridge.class);
        Path empty = Files.createDirectory(directory.resolve("empty"));
        for (String location : List.of("", empty.toString(), directory.resolve("missing").toString())) {
            assertThrows(IllegalStateException.class, () -> new KmKnowledgeBase(bridge, location, location, ""));
        }
        verify(bridge, never()).loadLibrary(any(), any());
    }

    private static Path library(Path parent, String name) throws Exception {
        Path path = Files.createDirectory(parent.resolve(name));
        Files.writeString(path.resolve("Fixture.km"), "(Fixture has (color (blue)))");
        return path;
    }

    private static KmEngineBridge.Evaluation loaded(KmEngineBridge.Session session) {
        return new KmEngineBridge.Evaluation(session, true, null, "(\"Fixture\")", null, null,
                false, List.of(), List.of(), List.of(), null, null);
    }

    private static KmEngineBridge.Evaluation failed(KmEngineBridge.Session session) {
        // KM can return :ok true while recording a failed form during load-lib.
        return new KmEngineBridge.Evaluation(session, true, null, "(\"Fixture\")", null, null,
                false, List.of("Invalid library form"), List.of(), List.of(), null, null);
    }
}
