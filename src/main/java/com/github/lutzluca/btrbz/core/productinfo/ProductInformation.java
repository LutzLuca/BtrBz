package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
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
import net.minecraft.world.inventory.Slot;
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
        @Nullable Double price,
        int stackCount,
        boolean isShiftHeld
    ) {
        var priceText = Component.literal(label).withStyle(ChatFormatting.AQUA);

        if (price != null) {
            var displayPrice = isShiftHeld && stackCount > 1 ? price * stackCount : price;
            priceText.append(Component
                .literal(Utils.formatDecimal(displayPrice, 1, true) + " coins")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            if (isShiftHeld && stackCount > 1) {
                priceText.append(Component
                    .literal(" (" + stackCount + "x)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            priceText.append(Component.literal("Not Available").withStyle(ChatFormatting.GRAY));
        }

        return priceText;
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
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
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
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)))
            .<Component>map(line -> line.withStyle(style -> style.withItalic(false))).toList();

        item.set(DataComponents.LORE, new ItemLore(loreLines));

        this.cachedProductInfoItem = item;
        this.cachedProductInfoSite = cfg.site;
        return this.cachedProductInfoItem.copy();
    }

    private void registerTooltipDisplay() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive()) {
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
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SHIFT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" Click ").withStyle(ChatFormatting.GRAY))
                .append(Component
                    .literal("to view on ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .withStyle(style -> style.withBold(false)))
                .append(Component
                    .literal(cfg.site.displayName())
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));

        });

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive()) {
                return;
            }
            var cfg = this.config.get();
            if (!cfg.enabled || !cfg.priceTooltipEnabled) {
                return;
            }

            var lookup = this.lookup(stack);
            if (lookup == null || lookup.prices() == null) {
                return;
            }

            var cached = lookup.prices();
            var count = priceCount(stack.getCount(), lookup.product(),
                lookup.singleItemPrice() || stack.getItem() == Items.ENCHANTED_BOOK);
            var isShiftHeld = Minecraft.getInstance().hasShiftDown();

            lines.add(Component.empty());

            if (count > 1 && !isShiftHeld) {
                lines.add(Component
                    .literal("Hold ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("SHIFT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(" to show for (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("x").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
            }

            if (count > 1 && isShiftHeld) {
                lines.add(Component
                    .literal("Showing price for ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("x").withStyle(ChatFormatting.GRAY)));
            }

            lines.add(this.createPriceText("Buy Price: ", cached.buyPrice, count, isShiftHeld));
            lines.add(this.createPriceText("Sell Price: ", cached.sellPrice, count, isShiftHeld));
        });
    }

    private boolean shouldApplyCtrlShiftClick(ItemStack stack) {
        if (!this.isCtrlShiftEnabled()) {
            return false;
        }

        var lookup = this.lookup(stack);
        return lookup != null
            && this.isCtrlShiftContextEnabled(this.isStackInPlayerInventory(stack))
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

    private @Nullable CachedProductLookup lookup(SlotView view) {
        // Clicks and tooltips resolve the displayed stack, which may be projected
        return this.lookup(view.getSlot().getItem(), view.playerInventorySlot() ? null : view.getSlot());
    }

    private @Nullable CachedProductLookup lookup(ItemStack stack) {
        return this.lookup(stack, this.hoveredSlot(stack)
            .filter(slot -> !GameUtils.isPlayerInventorySlot(slot))
            .orElse(null));
    }

    private @Nullable CachedProductLookup lookup(ItemStack stack, @Nullable Slot menuSlot) {
        if (stack.isEmpty()) {
            return null;
        }

        var cfg = this.config.get();
        var screen = ScreenTracker.get().getCurrInfo().getGenericContainerScreen().orElse(null);

        // Special Cases: Bazaar categories, Attribute Menu shards, order stacks and Experimentation Table
        if (menuSlot != null && screen != null
            && menuSlot.container == screen.getMenu().getContainer()) {
            var lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();

            if (ScreenTracker.inMenu(BazaarMenuType.Main) && !ProductInfoMatching.isBazaarProductEntry(lore)) {
                // Categories can share a product's name and ID. Their action identifies navigation
                return null;
            }

            var title = screen.getTitle().getString();
            if (ProductInfoMatching.isAttributeMenu(title)) {
                // Attribute titles describe progress, while the source holds the shard's name
                var shardName = ProductInfoMatching.attributeShardName(lore).orElse(null);
                if (shardName == null) {
                    return null;
                }

                var shard = this.bazaarData.resolveProductName(shardName);
                var indexed = this.bazaarData.resolveIndexedProduct(shard).orElse(null);
                if (indexed == null || !indexed.productId().startsWith("SHARD_")) {
                    return null;
                }
                return this.productLookupCache.create(shard, true);
            }

            if (this.isOrderScreenProductRow(stack)) {
                var order = OrderInfoParser.parseOrderInfo(stack, menuSlot.getContainerSlot(), this.bazaarData);
                if (order.isSuccess()) {
                    return this.productLookupCache.create(order.get().product(), false);
                }
            }

            var superpairs = ProductInfoMatching.isSuperpairsMenu(title);
            if (superpairs || ProductInfoMatching.isRngMenu(title)) {
                return this.lookupExperimentationReward(stack, lore, superpairs);
            }
        }

        // default lookup
        var lookup = this.productLookupCache.get(stack);
        if (!cfg.requireMatchingName) {
            return lookup;
        }

        var indexed = this.bazaarData.resolveIndexedProduct(lookup.product()).orElse(null);
        return indexed != null && ProductInfoMatching.matchesDisplayedName(
            stack.getHoverName().getString(), indexed,
            this.isIdentifiedEnchantmentBook(stack, indexed.productId()))
                ? lookup : null;
    }

    private @Nullable CachedProductLookup lookupExperimentationReward(
        ItemStack stack,
        List<Component> lore,
        boolean superpairs
    ) {
        var lookup = this.productLookupCache.get(stack);
        var indexed = this.bazaarData.resolveIndexedProduct(lookup.product()).orElse(null);
        var identifiedBook = indexed != null && this.isIdentifiedEnchantmentBook(stack, indexed.productId());

        var displayedName = stack.getHoverName().getString();
        var genericBook = superpairs && "Enchanted Book".equals(Utils.cleanDisplayName(displayedName));

        // Rewards normally name their product in the title. Generic Superpairs books use
        // the third lore line unless stack resolution already identifies the enchantment
        var rewardName = genericBook && !identifiedBook
            ? ProductInfoMatching.superpairsEnchantmentName(lore).orElse(null)
            : displayedName;
        if (rewardName == null) {
            return null;
        }

        // fallback to name lookup when stack resolution has no indexed product
        if (indexed == null) {
            var product = this.bazaarData.resolveProductName(rewardName);
            indexed = this.bazaarData.resolveIndexedProduct(product).orElse(null);
            lookup = this.productLookupCache.create(product, false);
        }
        if (genericBook && (indexed == null || !indexed.productId().startsWith("ENCHANTMENT_"))) {
            return null;
        }

        if (!this.config.get().requireMatchingName) {
            return lookup;
        }
        return indexed != null && ProductInfoMatching.matchesDisplayedName(rewardName, indexed, identifiedBook)
            ? lookup : null;
    }

    private boolean isIdentifiedEnchantmentBook(ItemStack stack, String productId) {
        // Trust the resolved enchantment ID when a book uses the generic title
        return stack.getItem() == Items.ENCHANTED_BOOK && productId.startsWith("ENCHANTMENT_");
    }

    static int priceCount(int stackCount, ProductIdentity product, boolean singleItemPrice) {
        return singleItemPrice || product.bazaarProductId().filter(id -> id.startsWith("ENCHANTMENT_")).isPresent()
            ? 1 : stackCount;
    }

    private boolean isOrderScreenProductRow(ItemStack stack) {
        return ScreenTracker.inMenu(BazaarMenuType.Orders)
            && GameUtils.orderScreenNonOrderItemsFilter(stack);
    }

    private Optional<Slot> hoveredSlot(ItemStack stack) {
        // Some mods give the tooltip code a copy of the item.
        // Check that it matches the item in the hovered slot.
        // Use that slot for menu lookup and player inventory checks.
        return ScreenTracker
            .get()
            .getCurrInfo()
            .getGenericContainerScreen()
            .map(screen -> screen instanceof AbstractContainerScreenAccessor accessor
                ? accessor.getHoveredSlot()
                : null)
            .filter(slot -> slot != null && ItemStack.matches(slot.getItem(), stack));
    }

    private boolean isStackInPlayerInventory(ItemStack stack) {
        var hoveredSlot = this.hoveredSlot(stack);
        if (hoveredSlot.isPresent()) {
            return GameUtils.isPlayerInventorySlot(hoveredSlot.get());
        }

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
        ProductIdentity product,
        @Nullable CachedPrice prices,
        boolean singleItemPrice
    ) {

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
            return true; // view displayed stack on click
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            if (!ctx.modifiers().controlDown() || !ctx.modifiers().shiftDown()) {
                return SlotClickResult.Pass;
            }

            if (!ProductInformation.this.isCtrlShiftEnabled()) {
                return SlotClickResult.Pass;
            }

            if (!ProductInformation.this.isCtrlShiftContextEnabled(ctx.view().playerInventorySlot())) {
                return SlotClickResult.Pass;
            }
            var lookup = ProductInformation.this.lookup(ctx.view());
            if (lookup == null) {
                return SlotClickResult.Pass;
            }

            var productId = lookup.marketProductId();
            if (productId.isEmpty()) {
                log.warn("No Bazaar product found for {}", ctx.view().getSlot().getItem().getHoverName().getString());
                return SlotClickResult.Pass;
            }

            var cfg = ProductInformation.this.config.get();
            ProductInformation.this.confirmAndOpen(cfg.site.format(productId.get()));
            return SlotClickResult.Consume;
        }
    }

    private class ProductLookupCache {

        private final WeakHashMap<ItemStack, CachedProductLookup> cache = new WeakHashMap<>();

        ProductLookupCache() {
            ProductInformation.this.bazaarData.addListener(products -> this.clear());
            ProductInformation.this.bazaarData.addIndexChangeListener(this::clear);
        }

        CachedProductLookup get(ItemStack stack) {
            var cached = this.cache.get(stack);
            if (cached != null) {
                return cached;
            }

            var lookup = this.create(ProductInformation.this.bazaarData.resolveProduct(stack), false);
            this.cache.put(stack, lookup);
            return lookup;
        }

        CachedProductLookup create(ProductIdentity product, boolean singleItemPrice) {
            var data = ProductInformation.this.bazaarData;
            CachedPrice prices = null;
            if (data.contains(product)) {
                prices = new CachedPrice(
                    data.lowestSellOfferPrice(product).orElse(null),
                    data.highestBuyOrderPrice(product).orElse(null));
            }

            return new CachedProductLookup(product, prices, singleItemPrice);
        }

        void clear() {
            if (!this.cache.isEmpty()) {
                log.trace("Clearing product lookup cache with {} mappings", this.cache.size());
            }
            this.cache.clear();
        }
    }
}
