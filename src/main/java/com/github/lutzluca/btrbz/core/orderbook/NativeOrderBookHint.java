package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

/** Annotates only the native Create Buy Order / Create Sell Offer price-level lore. */
public final class NativeOrderBookHint {
    private static final int BUY_ORDER_SLOT = 15;
    private static final int SELL_OFFER_SLOT = 16;

    private final BazaarProductContext productContext;
    private final TrackedOrderManager orders;
    private final Supplier<OrderBookHintConfig> config;

    public NativeOrderBookHint(
        BazaarProductContext productContext,
        TrackedOrderManager orders,
        Supplier<OrderBookHintConfig> config
    ) {
        this.productContext = productContext;
        this.orders = orders;
        this.config = config;

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> this.annotateHovered(stack, lines));
    }

    private void annotateHovered(ItemStack stack, List<Component> lines) {
        var settings = this.config.get();
        if (!BtrBz.isActive() || settings.mode == OrderBookHintConfig.Mode.Off) {
            return;
        }

        var info = ScreenTracker.get().getCurrInfo();
        if (!info.inMenu(ScreenTracker.BazaarMenuType.Item)) {
            return;
        }

        var screen = info.getGenericContainerScreen().orElse(null);
        var slot = GameUtils.hoveredSlot(stack).orElse(null);
        if (screen == null || slot == null || slot.container != screen.getMenu().getContainer()) {
            return;
        }

        OrderType side = switch (slot.getContainerSlot()) {
            case BUY_ORDER_SLOT -> OrderType.Buy;
            case SELL_OFFER_SLOT -> OrderType.Sell;
            default -> null;
        };
        var openedProduct = this.productContext.openedProduct();
        if (side == null || openedProduct == null) {
            return;
        }

        var product = ProductIdentity.fromIndex(openedProduct);
        annotate(lines, OrderBookMatches.from(product, this.orders.currentOrders()), side,
            settings.showRemainingInNativeBook, UiStyles.primary());
    }

    static void annotate(
        List<Component> lines,
        OrderBookMatches matches,
        OrderType side,
        boolean remaining,
        Style primary
    ) {
        for (int index = 0; index < lines.size(); index++) {
            var original = lines.get(index);
            var price = price(original.getString(), side);
            if (price.isEmpty()) {
                continue;
            }

            var match = matches.at(side, price.getAsDouble());
            if (match.count() == 0) {
                continue;
            }

            if (remaining) {
                if (index + 1 < lines.size() && lines.get(index + 1).getString().startsWith("  - Your orders: ")) {
                    continue;
                }

                String items = match.remaining().isPresent()
                    ? "~" + Utils.formatDecimal(match.remaining().getAsLong(), 0, true)
                        + (match.remaining().getAsLong() == 1 ? " item left" : " items left")
                    : "Remaining unknown";
                lines.add(++index, Component.literal("  - Your orders: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(Utils.formatDecimal(match.count(), 0, true)).withStyle(primary))
                    .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(items).withStyle(primary)));
            } else {
                lines.set(index, original.copy().append(
                    Component.literal(" (" + Utils.formatDecimal(match.count(), 0, true) + ")").withStyle(primary)));
            }
        }
    }

    /**
     * Buy: {@code - <price> coin(s) each | <volume>x in <count> order(s)}
     * Sell: {@code - <price> coin(s) each | <volume>x from <count> offer(s)}
     */
    static OptionalDouble price(String line, OrderType side) {
        var parts = StringUtils.split(line, ' ');
        String relation = side == OrderType.Buy ? "in" : "from";
        String unit = side == OrderType.Buy ? "order" : "offer";
        if (parts.length != 9 || !parts[0].equals("-")
            || !parts[3].equals("each")
            || !parts[4].equals("|")
            || !(parts[2].equals("coin") || parts[2].equals("coins"))
            || !parts[5].endsWith("x")
            || !parts[6].equals(relation)
            || !(parts[8].equals(unit) || parts[8].equals(unit + "s"))) {
            return OptionalDouble.empty();
        }

        var volume = Utils.parseUsFormattedNumber(parts[5].substring(0, parts[5].length() - 1));
        var count = Utils.parseUsFormattedNumber(parts[7]);
        if (volume.filter(value -> value.signum() > 0 && value.scale() <= 0).isFailure()
            || count.filter(value -> value.signum() > 0 && value.scale() <= 0).isFailure()) {
            return OptionalDouble.empty();
        }

        return Utils.parseUsFormattedNumber(parts[1])
            .filter(value -> OrderBookMatches.supportedPrice(value.doubleValue())
                && BigDecimal.valueOf(value.doubleValue()).compareTo(value) == 0)
            .map(value -> OptionalDouble.of(value.doubleValue())).getOrElse(OptionalDouble.empty());
    }
}
