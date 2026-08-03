package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetData;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetDefinition;
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
        OrderPresetsWidgetDefinition.create(null),
        OrderBookPriceWidgetDefinition.create(new OrderBookWidgetData(null), null)
    );

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
                ProductIdentity.fromName("Product"), Component.literal("Product"), Optional.empty()
            );
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
            product.isPresent() ? Optional.of(OrderType.Buy) : Optional.empty(), 1
        );
    }
}
