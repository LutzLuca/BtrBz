package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Slf4j
public class OrderHighlightManager {

    private final Map<Integer, TrackedOrder> slotToTrackedOrder = new HashMap<>();
    private final Map<Integer, Integer> filledOrderSlots = new HashMap<>();
    private Integer overrideSlotIdx = null;
    private Integer overrideColor = null;

    public static int colorForStatus(OrderStatus status) {
        return switch (status) {
            case OrderStatus.Top _ -> 0xFF55FF55;
            case OrderStatus.Matched _ -> 0xFF5555FF;
            case OrderStatus.Undercut _ -> 0xFFFF5555;
            case OrderStatus.Unknown _ -> 0xFFAA55FF;
        };
    }

    public void sync(
        List<TrackedOrder> trackedOrders,
        List<OrderInfo.FilledOrderInfo> filledOrders
    ) {
        log.debug("Synchronizing highlights from ui orders");
        this.slotToTrackedOrder.clear();
        this.filledOrderSlots.clear();

        trackedOrders
            .stream()
            .filter(order -> order.slot != -1)
            .forEach(order -> this.slotToTrackedOrder.put(order.slot, order));

        filledOrders.forEach(order -> this.filledOrderSlots.put(order.slotIdx(), 0xFFEFBF04));
    }

    public Optional<Integer> getHighlight(int idx) {
        if (this.overrideSlotIdx != null && idx == this.overrideSlotIdx) {
            return Optional.of(this.overrideColor);
        }

        if (!ConfigManager.get().orderHighlight.enabled) {
            return Optional.empty();
        }

        var tracked = this.slotToTrackedOrder.get(idx);
        if (tracked != null) {
            return Optional.of(colorForStatus(tracked.status));
        }

        return Optional.ofNullable(this.filledOrderSlots.get(idx));
    }

    public void setHighlightOverride(int slotIdx, int color) {
        this.overrideSlotIdx = slotIdx;
        this.overrideColor = color;
    }

    public void clearHighlightOverride() {
        this.overrideSlotIdx = null;
        this.overrideColor = null;
    }

    public TrackedOrder getTrackedOrder(int slotIdx) {
        return this.slotToTrackedOrder.get(slotIdx);
    }

    public static class HighlightConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Order Highlighting"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(ConfigScreen.createDescription(
                    "Draw status-colored backgrounds behind your orders on the Bazaar Orders page."))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Highlighting"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Color-code your orders on the Bazaar Orders page so their current status is easy to scan."),
                    highlightLegend()
                ),
                    ConfigScreen.ConfigImage.ORDER_STATUS
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }

        private static Component highlightLegend() {
            return Component.empty()
                .append(Component.literal("Green").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(": best price\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Blue").withStyle(ChatFormatting.BLUE))
                .append(Component.literal(": matched at the best price\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Red").withStyle(ChatFormatting.RED))
                .append(Component.literal(": undercut\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Purple").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(": status unknown\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Gold").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": filled and ready to claim").withStyle(ChatFormatting.GRAY));
        }
    }
}
