package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetProductContext;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Widget manager contextual support")
class WidgetManagerContextSupportTest {
    private final List<WidgetDefinition<?, ?, ?>> definitions = List.of(
        definition("btrbz:presets", OrderPresetsWidgetDefinition::supportsSession),
        definition("btrbz:book", OrderBookPriceWidgetDefinition::supportsSession));

    @Nested
    @DisplayName("sign workflows")
    class SignWorkflows {
        @Test
        @DisplayName("accepts the order preset sign")
        void acceptsPresetSign() {
            var session = session(Optional.empty(), Optional.of(BazaarMenuType.BuyOrderSetupVolume));

            assertTrue(WidgetRuntime.contextualManagerSupported(false, true, session, definitions));
        }

        @Test
        @DisplayName("accepts the order-book price sign")
        void acceptsOrderBookSign() {
            var product = new WidgetProductContext(
                ProductIdentity.fromName("Product"), Component.literal("Product"), Optional.empty());
            var session = session(Optional.of(product), Optional.of(BazaarMenuType.BuyOrderSetupPrice));

            assertTrue(WidgetRuntime.contextualManagerSupported(false, true, session, definitions));
        }

        @Test
        @DisplayName("rejects an ordinary sign")
        void rejectsOrdinarySign() {
            var session = session(Optional.empty(), Optional.empty());

            assertFalse(WidgetRuntime.contextualManagerSupported(false, true, session, definitions));
        }
    }

    private static WidgetSession session(
        Optional<WidgetProductContext> product,
        Optional<BazaarMenuType> previousMenu
    ) {
        return new WidgetSession(
            1, false, true, false,
            Optional.empty(), previousMenu, product,
            product.isPresent() ? Optional.of(OrderType.Buy) : Optional.empty(), 1);
    }

    private static WidgetDefinition<Object, TestConfig, Void> definition(
        String idValue,
        java.util.function.Predicate<WidgetSession> supports
    ) {
        var id = WidgetId.parse(idValue);
        var handle = new WidgetConfigHandle<>(
            id, TestConfig::new, TestConfig::new,
            value -> value.frame, (current, defaults) -> {});
        return WidgetDefinition.<Object, TestConfig, Void>builder(id, idValue)
            .config(handle)
            .supports(supports)
            .data(new WidgetDataSource<>() {
                @Override
                public CacheDependencies cacheDependencies() {
                    return CacheDependencies.none();
                }

                @Override
                public Object snapshot(WidgetSession session) {
                    return new Object();
                }
            })
            .preview(() -> null)
            .viewFactory(() -> null)
            .build();
    }

    private static final class TestConfig {
        private final WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0, 0));
    }
}
