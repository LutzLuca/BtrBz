package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.OrderManager.StatusUpdate;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.text.Text;

public class HighlightManager {

    private final Map<Integer, Integer> highlights = new HashMap<>();
    private Integer overrideSlotIdx = null;
    private Integer overrideColor = null;

    public static int colorForStatus(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top ignored -> 0xFF55FF55;
            case OrderStatus.Matched ignored -> 0xFF5555FF;
            case OrderStatus.Undercut ignored -> 0xFFFF5555;
            case OrderStatus.Unknown ignored -> 0xFFAA55FF;
        };
    }

    public Optional<Integer> getHighlight(int idx) {
        if (this.overrideSlotIdx != null && idx == this.overrideSlotIdx) {
            return Optional.of(this.overrideColor);
        }

        if (!ConfigManager.get().orderHighlight.enabled) {
            return Optional.empty();
        }

        return Optional.ofNullable(highlights.get(idx));
    }

    public void setStatuses(List<OrderInfo> parsedOrders) {
        this.highlights.clear();
        var orders = new ArrayList<>(parsedOrders);

        var trackedCopy = BtrBz.orderManager().getTrackedOrders();
        for (var tracked : trackedCopy) {
            orders.stream().filter(tracked::matches).findFirst().ifPresent(info -> {
                orders.remove(info);
                this.highlights.put(info.slotIdx(), colorForStatus(tracked.status));
            });
        }

        for (var info : orders) {
            var color = info.filled() ? 0xFFEFBF04 : 0xFFAA55FF;
            this.highlights.put(info.slotIdx(), color);
        }
    }

    public void updateStatus(StatusUpdate update) {
        int slotIdx = update.trackedOrder().slot;
        if (slotIdx < 0) {
            return;
        }

        this.highlights.put(slotIdx, colorForStatus(update.status()));
    }

    public void setHighlightOverride(int slotIdx, int color) {
        this.overrideSlotIdx = slotIdx;
        this.overrideColor = color;
    }


    public void clearHighlightOverride() {
        this.overrideSlotIdx = null;
        this.overrideColor = null;
    }

    public static class HighlightConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order Highlighting"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable order highlights in the Order screen")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var enabledBuilder = this.createEnabledOption();

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Highlighting"))
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable order highlights in the Order screen")))
                .option(enabledBuilder.build())
                .collapsed(false)
                .build();
        }
    }
}
