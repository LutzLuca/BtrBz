package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.SlotClickResult;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ProductInformation {

    private static final int CUSTOM_ITEM_IDX = 22;
    private final BazaarData bazaarData;
    private final BazaarProductContext productContext;
    private final Supplier<ProductInfoConfig> config;
    private final ProductLookupCache productLookupCache;

    private @Nullable ItemStack cachedProductInfoItem = null;
    private @Nullable ProductInfoConfig.Site cachedProductInfoSite = null;

    public ProductInformation(
        BazaarData bazaarData,
        BazaarProductContext productContext,
        Supplier<ProductInfoConfig> config
    ) {
        this.bazaarData = bazaarData;
        this.productContext = productContext;
        this.config = config;
        this.productLookupCache = new ProductLookupCache();
        ScreenTracker.registerOnSwitch(_ -> this.productLookupCache.clear());
        this.registerSlotHooks();
        this.registerTooltipDisplay();
    }

    private Component createPriceText(
        String label,
        @Nullable Double unitPrice,
        TooltipQuantity quantity,
        boolean isShiftHeld
    ) {
        var priceText = Component.literal(label).withStyle(ChatFormatting.YELLOW);

        if (unitPrice != null) {
            var multiplier = quantity.priceMultiplier(isShiftHeld);
            var displayPrice = unitPrice * multiplier;
            priceText.append(Component
                .literal(Utils.formatDecimal(displayPrice, 1, true) + " coins")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            if (multiplier > 1) {
                priceText.append(Component
                    .literal(
                        " ("
                            + Utils.formatDecimal(multiplier, 0, true)
                            + "x @ "
                            + Utils.formatDecimal(unitPrice, 1, true)
                            + " each)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            priceText.append(Component.literal("Not Available").withStyle(ChatFormatting.GRAY));
        }

        return priceText;
    }

    private static Component quantityText(int amount, String meaning, boolean isShiftHeld) {
        var amountText = Component
            .literal(Utils.formatDecimal(amount, 0, true) + "x")
            .withStyle(ChatFormatting.WHITE);
        if (isShiftHeld) {
            return Component
                .literal("Showing " + meaning + " for ")
                .withStyle(ChatFormatting.GRAY)
                .append(amountText);
        }
        return Component
            .literal("Hold ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("SHIFT").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            .append(Component
                .literal(" to show " + meaning + " for ")
                .withStyle(ChatFormatting.DARK_GRAY))
            .append(amountText);
    }

    private static void appendQuantityText(List<Component> lines, TooltipQuantity quantity, boolean isShiftHeld) {
        switch (quantity) {
            case TooltipQuantity.Stack stack -> {
                if (stack.amount() > 1) {
                    lines.add(ProductInformation.quantityText(stack.amount(), "total", isShiftHeld));
                }
            }
            case TooltipQuantity.OriginalOrder order -> {
                lines.add(ProductInformation.quantityText(order.amount(), "market total", isShiftHeld));
            }
            case TooltipQuantity.UnavailableOrder _ -> {
                if (isShiftHeld) {
                    lines.add(Component.literal("Order total unavailable. Showing unit prices.")
                        .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private void registerSlotHooks() {
        SlotHookRegistry.register(new InfoSiteButtonHook());
        SlotHookRegistry.register(new ProductLookupHook());
    }

    private ItemStack createProductInfoItem() {
        var cfg = this.config.get();

        if (this.cachedProductInfoItem != null && this.cachedProductInfoSite == cfg.site) {
            return this.cachedProductInfoItem.copy();
        }

        var item = new ItemStack(Items.PAPER);
        item.set(
            DataComponents.CUSTOM_NAME,
            Component
                .literal("Product Info")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .withStyle(style -> style.withItalic(false)));

        var loreLines = Stream.of(
            Component.literal("View detailed Bazaar statistics").withStyle(ChatFormatting.GRAY),
            Component.literal("and live market data for this item.").withStyle(ChatFormatting.GRAY),
            Component.empty(),
            Component
                .literal("➤ Click to open ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .withStyle(style -> style.withItalic(false))
                .append(Component
                    .literal(cfg.site.displayName())
                    .withStyle(ChatFormatting.GOLD)))
            .<Component>map(line -> line.withStyle(style -> style.withItalic(false))).toList();

        item.set(DataComponents.LORE, new ItemLore(loreLines));

        this.cachedProductInfoItem = item;
        this.cachedProductInfoSite = cfg.site;
        return this.cachedProductInfoItem.copy();
    }

    private void registerTooltipDisplay() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive() || stack.isEmpty()) {
                return;
            }
            var cfg = this.config.get();
            if (!cfg.enabled || !cfg.ctrlShiftEnabled) {
                return;
            }

            if (!this.shouldApplyCtrlShiftClick(stack)) {
                return;
            }

            lines.add(Component.empty());
            lines.add(Component
                .literal("CTRL")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SHIFT").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" Click ").withStyle(ChatFormatting.GRAY))
                .append(Component
                    .literal("to view on ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .withStyle(style -> style.withBold(false)))
                .append(Component
                    .literal(cfg.site.displayName())
                    .withStyle(ChatFormatting.GOLD)));

        });

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive() || stack.isEmpty()) {
                return;
            }
            var cfg = this.config.get();
            if (!cfg.enabled || !cfg.priceTooltipEnabled) {
                return;
            }

            var lookup = this.productLookupCache.get(stack);
            var cached = lookup.prices();
            if (cached == null) {
                return;
            }

            var isShiftHeld = Minecraft.getInstance().hasShiftDown();
            var quantity = lookup.quantity();

            lines.add(Component.empty());
            ProductInformation.appendQuantityText(lines, quantity, isShiftHeld);

            lines.add(this.createPriceText("Buy Price: ", cached.buyPrice, quantity, isShiftHeld));
            lines.add(this.createPriceText("Sell Price: ", cached.sellPrice, quantity, isShiftHeld));
        });
    }

    private boolean shouldApplyCtrlShiftClick(ItemStack stack) {
        if (!this.isCtrlShiftEnabled()) {
            return false;
        }

        var lookup = this.productLookupCache.get(stack);
        return this.isCtrlShiftContextEnabled(lookup.playerInventoryStack())
            && lookup.marketProductId().isPresent();
    }

    private boolean isCtrlShiftEnabled() {
        var cfg = this.config.get();
        return cfg.enabled && cfg.ctrlShiftEnabled;
    }

    private boolean isCtrlShiftContextEnabled(boolean playerInventoryStack) {
        var cfg = this.config.get();
        if (ScreenTracker.inBazaar()) {
            return playerInventoryStack || cfg.ctrlShiftOnBazaarItems;
        }

        return cfg.showOutsideBazaar;
    }

    private boolean isOrderScreenProductRow(ItemStack stack) {
        return ScreenTracker.inMenu(BazaarMenuType.Orders)
            && GameUtils.orderScreenNonOrderItemsFilter(stack);
    }

    private boolean isStackInPlayerInventory(ItemStack stack) {
        // Match the actual inventory stack, since an order card can show the same item.
        // noinspection DataFlowIssue
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        for (var playerStack : player.getInventory()) {
            if (playerStack == stack) {
                return true;
            }
        }

        return false;
    }

    private void confirmAndOpen(String link) {
        GameUtils.setScreen(new ConfirmLinkScreen(
            confirmed -> {
                if (confirmed) {
                    Try
                        .run(() -> Util.getPlatform().openUri(new URI(link)))
                        .onFailure(err -> Notifier.notifyPlayer(Component
                            .literal("Failed to open link: ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component
                                .literal(link)
                                .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BLUE))));
                }

                var prev = ScreenTracker.get().getPrevInfo();
                GameUtils.setScreen(prev != null ? prev.getScreen() : null);
            }, link, true));
    }

    private record CachedPrice(
        @Nullable Double buyPrice,
        @Nullable Double sellPrice
    ) {}

    private record CachedProductLookup(
        ItemStack snapshot,
        ProductIdentity product,
        boolean playerInventoryStack,
        @Nullable CachedPrice prices,
        TooltipQuantity quantity
    ) {

        boolean matches(ItemStack stack) {
            return this.snapshot.getCount() == stack.getCount()
                && ItemStack.isSameItemSameComponents(this.snapshot, stack);
        }

        Optional<String> marketProductId() {
            return this.prices != null ? this.product.bazaarProductId() : Optional.empty();
        }
    }

    private final class InfoSiteButtonHook implements SlotHook {

        private InfoSiteButtonHook() {}

        @Override
        public boolean matches(SlotView view) {
            var cfg = ProductInformation.this.config.get();
            return cfg.enabled
                && cfg.itemClickEnabled
                && ProductInformation.this.productContext.openedProduct() != null
                && !view.playerInventorySlot()
                && view.slotIdx() == CUSTOM_ITEM_IDX
                && view.getCurrInfo().inMenu(BazaarMenuType.Item);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            return ProductInformation.this.createProductInfoItem();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var cfg = ProductInformation.this.config.get();
            ProductInformation.this.confirmAndOpen(
                cfg.site.format(ProductInformation.this.productContext.openedProduct().productId()));
            return SlotClickResult.Consume;
        }
    }

    private final class ProductLookupHook implements SlotHook {

        private ProductLookupHook() {}

        @Override
        public boolean matches(SlotView view) {
            // Slot matching runs during rendering. Check lookup eligibility on click.
            return !view.getRawStack().isEmpty();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            if (!ctx.modifiers().controlDown() || !ctx.modifiers().shiftDown()) {
                return SlotClickResult.Pass;
            }

            if (!ProductInformation.this.isCtrlShiftEnabled()) {
                return SlotClickResult.Pass;
            }

            var lookup = ProductInformation.this.productLookupCache.get(ctx.view());
            if (!ProductInformation.this.isCtrlShiftContextEnabled(lookup.playerInventoryStack())) {
                return SlotClickResult.Pass;
            }

            var productId = lookup.marketProductId();
            if (productId.isEmpty()) {
                log.warn("No Bazaar product found for {}", ctx.view().getRawStack().getHoverName().getString());
                return SlotClickResult.Pass;
            }

            var cfg = ProductInformation.this.config.get();
            ProductInformation.this.confirmAndOpen(cfg.site.format(productId.get()));
            return SlotClickResult.Consume;
        }
    }

    private final class ProductLookupCache {

        private final WeakHashMap<ItemStack, CachedProductLookup> cache = new WeakHashMap<>();

        ProductLookupCache() {
            ProductInformation.this.bazaarData.addListener(products -> this.clear());
            ProductInformation.this.bazaarData.addIndexChangeListener(this::clear);
        }

        CachedProductLookup get(ItemStack stack) {
            var cached = this.cache.get(stack);
            if (cached != null && cached.matches(stack)) {
                return cached;
            }

            return this.cache(
                stack,
                ProductInformation.this.isStackInPlayerInventory(stack));
        }

        CachedProductLookup get(SlotView view) {
            var stack = view.getRawStack();
            var cached = this.cache.get(stack);
            if (cached != null && cached.matches(stack)
                && cached.playerInventoryStack() == view.playerInventorySlot()) {
                return cached;
            }

            return this.cache(
                stack,
                view.playerInventorySlot());
        }

        private CachedProductLookup cache(
            ItemStack stack,
            boolean playerInventoryStack
        ) {
            var data = ProductInformation.this.bazaarData;
            ProductIdentity product;
            TooltipQuantity quantity;
            if (!playerInventoryStack && ProductInformation.this.isOrderScreenProductRow(stack)) {
                // This lookup needs the product and original quantity, so the slot index can be -1.
                var order = OrderInfoParser.parseOrderInfo(stack, -1, data);
                product = order.map(info -> info.product()).getOrElse(() -> data.resolveProduct(stack));
                quantity = order
                    .<TooltipQuantity>map(info -> new TooltipQuantity.OriginalOrder(info.volume()))
                    .getOrElse(TooltipQuantity.UnavailableOrder::new);
            } else {
                product = data.resolveProduct(stack);
                quantity = new TooltipQuantity.Stack(stack.getItem() == Items.ENCHANTED_BOOK ? 1 : stack.getCount());
            }
            CachedPrice prices = null;
            if (data.contains(product)) {
                prices = new CachedPrice(
                    data.lowestSellOfferPrice(product).orElse(null),
                    data.highestBuyOrderPrice(product).orElse(null));
            }

            var cached = new CachedProductLookup(
                stack.copy(),
                product,
                playerInventoryStack,
                prices,
                quantity);
            this.cache.put(stack, cached);
            return cached;
        }

        void clear() {
            if (!this.cache.isEmpty()) {
                log.trace("Clearing product lookup cache with {} mappings", this.cache.size());
            }
            this.cache.clear();
        }
    }
}
