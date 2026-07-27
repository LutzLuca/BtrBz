package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PriceDifferenceWidgetData {
    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;
    private final BazaarData market;

    public PriceDifferenceWidgetData(BazaarData market) {
        this.market = market;
    }

    public Snapshot snapshot() {
        var info = ScreenInfoHelper.get().getCurrInfo();
        var productStack = info.getItemStack(PRODUCT_SLOT).orElse(ItemStack.EMPTY);
        int quantity = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount).orElse(0);
        if (productStack.isEmpty() || quantity <= 0) return empty();
        var product = this.market.resolveProduct(productStack);
        var spread = this.market.productSpread(product);
        if (spread.isEmpty()) return empty();
        return new Snapshot(
            productStack.getHoverName().getString(), productStack.copy(), Math.round(spread.get()), quantity
        );
    }

    public static Snapshot preview() {
        return new Snapshot(
            "Enchanted Diamond", new ItemStack(Items.DIAMOND), 12_450, 640
        );
    }

    private Optional<Integer> listedCount(ItemStack stack) {
        return GameUtils.getLore(stack).stream()
            .filter(line -> line.startsWith("Inventory"))
            .findFirst()
            .flatMap(line -> Utils.parseUsFormattedNumber(
                line.replace("Inventory:", "").replace("items", "").trim()
            ).toJavaOptional())
            .map(Number::intValue);
    }

    private static Snapshot empty() {
        return new Snapshot("", ItemStack.EMPTY, 0, 0);
    }

    public record Snapshot(String productName, ItemStack icon, long perItem, int quantity) {
        public Snapshot {
            icon = icon.copy();
        }

        @Override
        public ItemStack icon() {
            return this.icon.copy();
        }

        public ItemStack iconCopy() {
            return this.icon.copy();
        }

        public long total() {
            return this.perItem * this.quantity;
        }
    }
}
