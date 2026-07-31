package com.github.lutzluca.btrbz.core.widgets.data;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Bazaar widget view data snapshots")
class BazaarWidgetViewDataSnapshotTest {
    @Test
    void copiesNestedMutableComponentsAtConstructionAndAccessBoundaries() {
        var name = Component.literal("Original");
        var tooltip = Component.literal("Tooltip");
        var order = new BazaarWidgetViewData.Order(
            new TrackedOrderId(UUID.randomUUID()),
            BazaarWidgetViewData.OrderSide.Buy,
            "Original",
            name,
            Optional.empty(),
            100,
            2,
            Optional.of(new BazaarWidgetViewData.FillProgress(0, 2)),
            BazaarWidgetViewData.OrderStatus.Top,
            Optional.empty(),
            List.of(tooltip)
        );
        var snapshot = new BazaarWidgetViewData.OrdersData(List.of(order));

        name.append(" changed");
        tooltip.append(" changed");
        ((MutableComponent) snapshot.orders().getFirst().formattedItemName()).append(" accessor mutation");
        ((MutableComponent) snapshot.orders().getFirst().tooltipLines().getFirst()).append(" accessor mutation");

        assertEquals("Original", snapshot.orders().getFirst().formattedItemName().getString());
        assertEquals("Tooltip", snapshot.orders().getFirst().tooltipLines().getFirst().getString());
    }
}
