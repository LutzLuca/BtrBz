package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bazaar HUD hint lifecycle")
class BazaarHudHintControllerTest {
    @Nested
    @DisplayName("completed renders")
    class CompletedRenders {
        @Test
        @DisplayName("the first Bazaar HUD render sends and persists one message")
        void firstBazaarRenderSendsOneMessage() {
            var context = new TestContext();

            context.controller.onWidgetRendered(WidgetId.of(
                Identifier.fromNamespaceAndPath("btrbz", "another_widget")));
            assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Unseen,
                context.config.supportedToggleHintState());

            context.controller.onWidgetRendered(BazaarOrdersWidgetDefinition.ID);
            context.controller.onWidgetRendered(BazaarOrdersWidgetDefinition.ID);

            assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Shown,
                context.config.supportedToggleHintState());
            assertEquals(1, context.saves.get());
            assertEquals(1, context.messages.size());
            assertEquals(1, context.keyReads.get());
        }
    }

    @Nested
    @DisplayName("dismissal")
    class Dismissal {
        @Test
        @DisplayName("dismisses only after the HUD has rendered")
        void dismissesOnlyAfterRender() {
            var unseen = new TestContext();
            assertFalse(unseen.controller.dismiss());
            assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Unseen,
                unseen.config.supportedToggleHintState());

            var shown = new TestContext();
            shown.controller.onWidgetRendered(BazaarOrdersWidgetDefinition.ID);
            assertTrue(shown.controller.dismiss());
            assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Dismissed,
                shown.config.supportedToggleHintState());
            assertFalse(shown.controller.dismiss());
        }

        @Test
        @DisplayName("a dismissed hint never sends chat")
        void dismissedHintNeverSendsChat() {
            var context = new TestContext();
            context.config.toggleHintState = BazaarOrdersWidgetConfig.ToggleHintState.Dismissed;

            context.controller.onWidgetRendered(BazaarOrdersWidgetDefinition.ID);

            assertEquals(0, context.messages.size());
            assertEquals(0, context.saves.get());
        }
    }

    private static final class TestContext {
        private final BazaarOrdersWidgetConfig config = new BazaarOrdersWidgetConfig();
        private final AtomicInteger saves = new AtomicInteger();
        private final AtomicInteger keyReads = new AtomicInteger();
        private final ArrayList<Component> messages = new ArrayList<>();
        private final BazaarHudHintController controller;

        private TestContext() {
            var handle = new WidgetConfigHandle<>(
                BazaarOrdersWidgetDefinition.ID,
                () -> this.config,
                BazaarOrdersWidgetConfig::new,
                value -> value.frame,
                BazaarOrdersWidgetConfig::resetPreferences);
            this.controller = new BazaarHudHintController(
                handle,
                () -> {
                    this.keyReads.incrementAndGet();
                    return Component.literal("J");
                },
                this.saves::incrementAndGet,
                this.messages::add);
        }
    }
}
