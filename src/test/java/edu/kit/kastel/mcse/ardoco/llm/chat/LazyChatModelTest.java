/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Tests that {@link LazyChatModel} defers delegate creation until first use and creates it only once.
 */
@NullMarked
class LazyChatModelTest {

    @Test
    @DisplayName("the delegate supplier runs only on first use, exactly once")
    void lazyInitialization() {
        AtomicInteger creations = new AtomicInteger();
        ChatModel delegate = mock(ChatModel.class);
        Supplier<ChatModel> supplier = () -> {
            creations.incrementAndGet();
            return delegate;
        };

        LazyChatModel lazy = new LazyChatModel(supplier);
        assertEquals(0, creations.get(), "supplier must not run at construction");

        when(delegate.chat("hi")).thenReturn("there");
        assertEquals("there", lazy.chat("hi"));
        lazy.chat("hi");

        assertEquals(1, creations.get(), "delegate must be created exactly once");
        verify(delegate, times(2)).chat("hi");
    }

    @Test
    @DisplayName("a null supplier is rejected")
    void nullSupplier() {
        assertThrows(NullPointerException.class, () -> new LazyChatModel(null));
    }

    @Test
    @DisplayName("all non-static ChatModel methods are overridden by LazyChatModel")
    void overridesAllChatModelMethods() {
        List<String> missing = Arrays.stream(ChatModel.class.getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !isDeclaredByLazyChatModel(m))
                .map(Method::toGenericString)
                .toList();
        assertTrue(missing.isEmpty(), "LazyChatModel does not override: " + missing);
    }

    private boolean isDeclaredByLazyChatModel(Method interfaceMethod) {
        try {
            LazyChatModel.class.getDeclaredMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes());
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
