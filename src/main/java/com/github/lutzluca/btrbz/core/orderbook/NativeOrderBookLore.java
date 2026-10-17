package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import io.vavr.control.Try;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Annotates only the native Create Buy Order / Create Sell Offer price-level lore. */
public final class NativeOrderBookLore {
    private final BazaarData market;
    private final TrackedOrderManager orders;
    private final Supplier<OwnOrderBookConfig> config;

    public NativeOrderBookLore(BazaarData market, TrackedOrderManager orders, Supplier<OwnOrderBookConfig> config) {
        this.market = market;
        this.orders = orders;
        this.config = config;
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> this.annotateHovered(stack, lines));
    }

    private void annotateHovered(ItemStack stack, List<Component> lines) {
        var settings = this.config.get();
        if (!BtrBz.isActive() || settings.mode == OwnOrderBookConfig.Mode.Off) {
            return;
        }
        var info = ScreenTracker.get().getCurrInfo();
        if (!info.inMenu(ScreenTracker.BazaarMenuType.Item)) {
            return;
        }
        var screen = info.getGenericContainerScreen().orElse(null);
        if (!(screen instanceof AbstractContainerScreenAccessor accessor)) {
            return;
        }
        var slot = accessor.getHoveredSlot();
        if (slot == null || slot.container != screen.getMenu().getContainer()
            || !ItemStack.matches(slot.getItem(), stack)) {
            return;
        }
        OrderType side;
        if (stack.is(Items.FILLED_MAP) && stack.getHoverName().getString().equals("Create Buy Order")) {
            side = OrderType.Buy;
        } else if (stack.is(Items.MAP) && stack.getHoverName().getString().equals("Create Sell Offer")) {
            side = OrderType.Sell;
        } else {
            return;
        }
        var product = info.getItemStack(13).map(this.market::resolveProduct).orElse(null);
        if (product == null || product.bazaarProductId().isEmpty()) {
            return;
        }
        annotate(lines, OwnOrderLevels.from(product, this.orders.currentOrders()), side,
            settings.showRemainingInNativeLore, UiStyles.primary());
    }

    static void annotate(List<Component> lines, OwnOrderLevels own, OrderType side, boolean remaining, Style primary) {
        var format = NumberFormat.getIntegerInstance(Locale.US);
        for (int index = 0; index < lines.size(); index++) {
            var original = lines.get(index);
            var price = price(original.getString(), side);
            if (price.isEmpty()) {
                continue;
            }
            var match = own.at(side, price.getAsDouble());
            if (match.count() == 0) {
                continue;
            }
            if (remaining) {
                // Tooltip callbacks normally get fresh lore. Do not duplicate our inserted line on reuse.
                if (index + 1 < lines.size() && lines.get(index + 1).getString().startsWith("  - Your orders: ")) {
                    continue;
                }
                String items = match.remaining().isPresent()
                    ? "~" + format.format(match.remaining().getAsLong())
                        + (match.remaining().getAsLong() == 1 ? " item left" : " items left")
                    : "Remaining unknown";
                lines.add(++index, Component.literal("  - Your orders: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(format.format(match.count())).withStyle(primary))
                    .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(items).withStyle(primary)));
            } else {
                // Strict parsing rejects the appended suffix on a repeated callback.
                lines.set(index, original.copy().append(
                    Component.literal(" (" + format.format(match.count()) + ")").withStyle(primary)));
            }
        }
    }

    static OptionalDouble price(String line, OrderType side) {
        if (!line.startsWith("- ")) {
            return OptionalDouble.empty();
        }
        int separator = line.indexOf(" each | ", 2);
        if (separator < 0) {
            return OptionalDouble.empty();
        }
        String priced = line.substring(2, separator);
        String suffix = priced.endsWith(" coins") ? " coins" : " coin";
        if (!priced.endsWith(suffix)) {
            return OptionalDouble.empty();
        }
        var priceToken = priced.substring(0, priced.length() - suffix.length());
        String rest = line.substring(separator + " each | ".length());
        String join = side == OrderType.Buy ? "x in " : "x from ";
        int quantityEnd = rest.indexOf(join);
        if (quantityEnd < 0) {
            return OptionalDouble.empty();
        }
        String quantity = rest.substring(0, quantityEnd);
        String countAndUnit = rest.substring(quantityEnd + join.length());
        String unit = side == OrderType.Buy ? " order" : " offer";
        String countSuffix = countAndUnit.endsWith(unit + "s") ? unit + "s" : unit;
        if (!countAndUnit.endsWith(countSuffix)) {
            return OptionalDouble.empty();
        }
        String count = countAndUnit.substring(0, countAndUnit.length() - countSuffix.length());
        if (!number(priceToken, true) || !number(quantity, false) || !number(count, false)) {
            return OptionalDouble.empty();
        }
        return Try.of(() -> new BigDecimal(priceToken.replace(",", "")))
            .filter(value -> OwnOrderLevels.supportedPrice(value.doubleValue())
                && BigDecimal.valueOf(value.doubleValue()).compareTo(value) == 0)
            .map(value -> OptionalDouble.of(value.doubleValue())).getOrElse(OptionalDouble.empty());
    }

    /** Entire decimal token, with optional correctly grouped thousands and at most one decimal place. */
    private static boolean number(String token, boolean decimal) {
        if (token.isEmpty()) {
            return false;
        }
        int digits = 0;
        boolean grouped = false;
        boolean fraction = false;
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character >= '0' && character <= '9') {
                digits++;
            } else if (character == ',' && !fraction
                && digits > 0
                && (grouped ? digits == 3 : digits <= 3)) {
                grouped = true;
                digits = 0;
            } else if (character == '.' && decimal && !fraction && digits > 0 && (!grouped || digits == 3)) {
                fraction = true;
                digits = 0;
            } else {
                return false;
            }
        }
        return digits > 0 && (fraction ? digits == 1 : !grouped || digits == 3);
    }
}
