package org.springy.som.nlp.km.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void loadsTheBundledConfiguredExpressionsInOrderBeforePublishingTheSession() {
        var core = entry("core");
        var som = entry("som");
        var bridge = mock(KmEngineBridge.class);
        var initial = mock(KmEngineBridge.Session.class);
        var coreLoaded = mock(KmEngineBridge.Session.class);
        var somLoaded = mock(KmEngineBridge.Session.class);
        when(bridge.configuredLibraries()).thenReturn(List.of(core, som));
        when(bridge.newSession()).thenReturn(initial);
        when(bridge.evaluate(initial, core.expression())).thenReturn(loaded(coreLoaded));
        when(bridge.evaluate(coreLoaded, som.expression())).thenReturn(loaded(somLoaded));

        var knowledgeBase = new KmKnowledgeBase(bridge, "");

        assertSame(somLoaded, knowledgeBase.startupSession());
        var order = inOrder(bridge);
        order.verify(bridge).configuredLibraries();
        order.verify(bridge).newSession();
        order.verify(bridge).evaluate(initial, core.expression());
        order.verify(bridge).evaluate(coreLoaded, som.expression());
    }

    @Test
    void stopsBeforeTheSecondConfiguredEntryIfTheFirstFails() {
        var core = entry("core");
        var som = entry("som");
        var bridge = mock(KmEngineBridge.class);
        var initial = mock(KmEngineBridge.Session.class);
        when(bridge.configuredLibraries()).thenReturn(List.of(core, som));
        when(bridge.newSession()).thenReturn(initial);
        when(bridge.evaluate(initial, core.expression())).thenReturn(failed(initial));

        var failure = assertThrows(IllegalStateException.class, () -> new KmKnowledgeBase(bridge, ""));

        assertTrue(failure.getMessage().contains(core.directory()));
        verify(bridge, never()).evaluate(any(), eq(som.expression()));
    }

    @Test
    void failedConfiguredEntryPreventsTheApiBeansFromStarting() {
        var core = entry("core");
        var som = entry("som");
        var bridge = mock(KmEngineBridge.class);
        var initial = mock(KmEngineBridge.Session.class);
        var coreLoaded = mock(KmEngineBridge.Session.class);
        when(bridge.configuredLibraries()).thenReturn(List.of(core, som));
        when(bridge.newSession()).thenReturn(initial);
        when(bridge.evaluate(initial, core.expression())).thenReturn(loaded(coreLoaded));
        when(bridge.evaluate(coreLoaded, som.expression())).thenReturn(failed(coreLoaded));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(KmEngineBridge.class, () -> bridge);
            context.registerBean(KmKnowledgeBase.class, () -> new KmKnowledgeBase(bridge, ""));
            context.registerBean("kmEngineService", KmEngineService.class);
            context.registerBean("kmController", KmController.class);

            var failure = assertThrows(BeanCreationException.class, context::refresh);

            assertTrue(failure.getMostSpecificCause().getMessage().contains(som.directory()));
            assertFalse(context.getBeanFactory().containsSingleton("kmController"));
            assertFalse(context.getBeanFactory().containsSingleton("kmEngineService"));
        }
    }

    @Test
    void acceptsAnErrorFreeEmptyLoadSuchAsSpecs() {
        var specs = entry("specs");
        var bridge = mock(KmEngineBridge.class);
        var initial = mock(KmEngineBridge.Session.class);
        when(bridge.configuredLibraries()).thenReturn(List.of(specs));
        when(bridge.newSession()).thenReturn(initial);
        when(bridge.evaluate(initial, specs.expression())).thenReturn(
                new KmEngineBridge.Evaluation(initial, true, null, "nil", null, null,
                        false, List.of(), List.of(), List.of(), null, null));

        assertSame(initial, new KmKnowledgeBase(bridge, "").startupSession());
    }

    @Test
    void stopsWhenTheBundledConfigurationCannotBeRead() {
        var bridge = mock(KmEngineBridge.class);
        when(bridge.configuredLibraries()).thenThrow(new IllegalStateException("Missing bundled configuration"));

        assertThrows(IllegalStateException.class, () -> new KmKnowledgeBase(bridge, ""));
        verify(bridge, never()).newSession();
    }

    @Test
    void rejectsAnEmptyLibrarySelection() {
        var bridge = mock(KmEngineBridge.class);
        when(bridge.configuredLibraries()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> new KmKnowledgeBase(bridge, ""));
        verify(bridge, never()).newSession();
    }

    private static KmEngineBridge.Library entry(String name) {
        String directory = "src/resources/" + name;
        return new KmEngineBridge.Library(directory, "component library " + directory,
                "(load-lib \"" + directory + "\" :silent t)");
    }

    private static KmEngineBridge.Evaluation loaded(KmEngineBridge.Session session) {
        return new KmEngineBridge.Evaluation(session, true, null, "(\"Fixture\")", null, null,
                false, List.of(), List.of(), List.of(), null, null);
    }

    private static KmEngineBridge.Evaluation failed(KmEngineBridge.Session session) {
        // KM can return :ok true while recording a failed form during load-lib.
        return new KmEngineBridge.Evaluation(session, true, null, "nil", null, null,
                false, List.of("Invalid or missing library"), List.of(), List.of(), null, null);
    }
}
